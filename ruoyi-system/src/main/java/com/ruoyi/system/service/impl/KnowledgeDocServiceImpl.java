package com.ruoyi.system.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.Oss.AliOssService;
import com.ruoyi.system.domain.KnowledgeDoc;
import com.ruoyi.system.mapper.KnowledgeDocMapper;
import com.ruoyi.system.service.IKnowledgeDocService;
import com.ruoyi.system.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class KnowledgeDocServiceImpl implements IKnowledgeDocService
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocServiceImpl.class);

    private static final String SYNC_STATUS_PENDING = "0";
    private static final String SYNC_STATUS_SYNCING = "1";
    private static final String SYNC_STATUS_SUCCESS = "2";

    @Autowired
    private KnowledgeDocMapper knowledgeDocMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RagService ragService;

    @Autowired
    private AliOssService aliOssService;

    @Value("${dify.api-key:}")
    private String difyApiKey;

    @Value("${dify.knowledge.upload-url:http://localhost:8082/v1/datasets/documents}")
    private String difyKnowledgeUploadUrl;

    @Value("${dify.knowledge.dataset-id:mock-dataset-id}")
    private String difyDatasetId;

    @Value("${dify.knowledge.mock-enabled:true}")
    private boolean mockUploadEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public KnowledgeDoc selectKnowledgeDocByDocId(Long docId)
    {
        return knowledgeDocMapper.selectKnowledgeDocByDocId(docId);
    }

    @Override
    public List<KnowledgeDoc> selectKnowledgeDocList(KnowledgeDoc knowledgeDoc)
    {
        return knowledgeDocMapper.selectKnowledgeDocList(knowledgeDoc);
    }

    @Override
    public int insertKnowledgeDoc(KnowledgeDoc knowledgeDoc)
    {
        knowledgeDoc.setCreateTime(DateUtils.getNowDate());
        if (StringUtils.isEmpty(knowledgeDoc.getSyncStatus()))
        {
            knowledgeDoc.setSyncStatus(SYNC_STATUS_PENDING);
        }

        int rows = knowledgeDocMapper.insertKnowledgeDoc(knowledgeDoc);
        if (rows > 0 && knowledgeDoc.getDocId() != null)
        {
            // Use Spring proxy so @Async is applied.
            applicationContext.getBean(IKnowledgeDocService.class).asyncUploadToDifyEngine(knowledgeDoc.getDocId());
        }
        return rows;
    }

    @Async("threadPoolTaskExecutor")
    @Override
    public void asyncUploadToDifyEngine(Long docId)
    {
        updateSyncStatus(docId, SYNC_STATUS_SYNCING);
        try
        {
            KnowledgeDoc knowledgeDoc = knowledgeDocMapper.selectKnowledgeDocByDocId(docId);
            if (knowledgeDoc == null)
            {
                throw new IllegalArgumentException("knowledge doc not found");
            }
            if (StringUtils.isEmpty(knowledgeDoc.getFileUrl()))
            {
                throw new IllegalArgumentException("knowledge doc file path is empty");
            }

            uploadToDifyKnowledge(knowledgeDoc);
            updateSyncStatus(docId, SYNC_STATUS_SUCCESS);
            log.info("Knowledge doc synced to Dify successfully, docId={}, datasetId={}", docId, difyDatasetId);
        }
        catch (Exception e)
        {
            updateSyncStatus(docId, SYNC_STATUS_PENDING);
            log.error("Knowledge doc sync to Dify failed, docId={}", docId, e);
        }
    }

    @Override
    public int updateKnowledgeDoc(KnowledgeDoc knowledgeDoc)
    {
        knowledgeDoc.setUpdateTime(DateUtils.getNowDate());
        return knowledgeDocMapper.updateKnowledgeDoc(knowledgeDoc);
    }

    @Override
    public int deleteKnowledgeDocByDocIds(Long[] docIds)
    {
        if (docIds == null || docIds.length == 0)
        {
            throw new ServiceException("待删除文档不能为空");
        }

        int successCount = 0;
        StringBuilder failedDocs = new StringBuilder();
        for (Long docId : docIds)
        {
            try
            {
                successCount += deleteSingleKnowledgeDoc(docId);
            }
            catch (Exception ex)
            {
                log.error("Knowledge doc delete failed, docId={}", docId, ex);
                if (failedDocs.length() > 0)
                {
                    failedDocs.append("; ");
                }
                failedDocs.append("docId=").append(docId).append(", reason=").append(ex.getMessage());
            }
        }

        if (failedDocs.length() > 0)
        {
            throw new ServiceException("知识库删除存在失败项: success=" + successCount + ", " + failedDocs);
        }
        return successCount;
    }

    @Override
    public int deleteKnowledgeDocByDocId(Long docId)
    {
        return deleteSingleKnowledgeDoc(docId);
    }

    private void uploadToDifyKnowledge(KnowledgeDoc knowledgeDoc)
    {
        Map<String, Object> requestBody = new HashMap<>(4);
        requestBody.put("datasetId", difyDatasetId);
        requestBody.put("docId", knowledgeDoc.getDocId());
        requestBody.put("docName", knowledgeDoc.getDocName());
        requestBody.put("filePath", knowledgeDoc.getFileUrl());

        if (mockUploadEnabled)
        {
            log.info("Mock call Dify Knowledge API, url={}, payload={}", difyKnowledgeUploadUrl, requestBody);
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.isNotEmpty(difyApiKey))
        {
            String authorization = difyApiKey.startsWith("Bearer ") ? difyApiKey : "Bearer " + difyApiKey;
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(difyKnowledgeUploadUrl, requestEntity, String.class);
        if (!response.getStatusCode().is2xxSuccessful())
        {
            throw new IllegalStateException("Dify Knowledge API call failed, status=" + response.getStatusCode());
        }
    }

    private void updateSyncStatus(Long docId, String syncStatus)
    {
        KnowledgeDoc updateDoc = new KnowledgeDoc();
        updateDoc.setDocId(docId);
        updateDoc.setSyncStatus(syncStatus);
        updateDoc.setUpdateTime(DateUtils.getNowDate());
        knowledgeDocMapper.updateKnowledgeDoc(updateDoc);
    }

    private int deleteSingleKnowledgeDoc(Long docId)
    {
        if (docId == null)
        {
            throw new ServiceException("docId 不能为空");
        }

        KnowledgeDoc knowledgeDoc = knowledgeDocMapper.selectKnowledgeDocByDocId(docId);
        if (knowledgeDoc == null)
        {
            throw new ServiceException("知识库文档不存在, docId=" + docId);
        }
        if (StringUtils.isEmpty(knowledgeDoc.getFileUrl()))
        {
            throw new ServiceException("知识库文档 fileUrl 为空, docId=" + docId);
        }

        log.info("Deleting knowledge doc resources, docId={}, docName={}, fileUrl={}",
            knowledgeDoc.getDocId(), knowledgeDoc.getDocName(), knowledgeDoc.getFileUrl());

        ragService.deleteByFileUrl(knowledgeDoc.getFileUrl());
        aliOssService.deleteObjectByUrl(knowledgeDoc.getFileUrl());

        int rows = knowledgeDocMapper.deleteKnowledgeDocByDocId(docId);
        if (rows <= 0)
        {
            throw new ServiceException("数据库删除失败, docId=" + docId);
        }

        log.info("Knowledge doc deleted successfully, docId={}, docName={}",
            knowledgeDoc.getDocId(), knowledgeDoc.getDocName());
        return rows;
    }
}
