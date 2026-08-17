package com.ruoyi.system.service.impl;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.Oss.AliOssService;
import com.ruoyi.system.domain.KnowledgeDoc;
import com.ruoyi.system.mapper.KnowledgeDocMapper;
import com.ruoyi.system.service.IKnowledgeDocService;
import com.ruoyi.system.service.RagService;
import com.ruoyi.system.service.KnowledgeBaseVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeDocServiceImpl implements IKnowledgeDocService
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocServiceImpl.class);
    private static final String STATUS_FAILED = "0";
    private static final String STATUS_SYNCING = "1";
    private static final String STATUS_SUCCESS = "2";
    private static final Set<String> ALLOWED_TYPES = Set.of("pdf", "doc", "docx", "txt", "md", "html", "htm");

    private final KnowledgeDocMapper knowledgeDocMapper;
    private final RagService ragService;
    private final AliOssService aliOssService;
    private final KnowledgeBaseVersionService versionService;

    public KnowledgeDocServiceImpl(KnowledgeDocMapper knowledgeDocMapper, RagService ragService,
        AliOssService aliOssService, KnowledgeBaseVersionService versionService)
    {
        this.knowledgeDocMapper = knowledgeDocMapper;
        this.ragService = ragService;
        this.aliOssService = aliOssService;
        this.versionService = versionService;
    }

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
    public KnowledgeDoc importFile(MultipartFile file, String docName, String remark)
    {
        validateFile(file);
        String contentHash = sha256(file);
        KnowledgeDoc duplicate = knowledgeDocMapper.selectKnowledgeDocByContentHash(contentHash);
        if (duplicate != null)
        {
            throw new ServiceException("相同内容的文档已存在, docId=" + duplicate.getDocId());
        }

        String fileUrl = aliOssService.upload(file);
        KnowledgeDoc document = new KnowledgeDoc();
        document.setDocName(resolveDocName(docName, file.getOriginalFilename()));
        document.setFileUrl(fileUrl);
        document.setContentHash(contentHash);
        document.setDocumentType(documentType(file.getOriginalFilename()));
        document.setRemark(remark);
        document.setStatus(STATUS_SYNCING);
        document.setCreateTime(DateUtils.getNowDate());
        try
        {
            knowledgeDocMapper.insertKnowledgeDoc(document);
            ragService.importOssFileToVectorStore(document);
            updateStatus(document.getDocId(), STATUS_SUCCESS);
            versionService.increment();
            document.setStatus(STATUS_SUCCESS);
            return document;
        }
        catch (Exception ex)
        {
            rollbackFailedImport(document);
            throw new ServiceException("文档向量化失败，已执行入库回滚: " + ex.getMessage());
        }
    }

    @Override
    public KnowledgeDoc replaceFile(Long docId, MultipartFile file, String docName, String remark)
    {
        validateFile(file);
        KnowledgeDoc existing = requireDocument(docId);
        String contentHash = sha256(file);
        KnowledgeDoc duplicate = knowledgeDocMapper.selectKnowledgeDocByContentHash(contentHash);
        if (duplicate != null && !duplicate.getDocId().equals(docId))
        {
            throw new ServiceException("相同内容的文档已存在, docId=" + duplicate.getDocId());
        }

        KnowledgeDoc previous = snapshot(existing);
        String oldFileUrl = previous.getFileUrl();
        String newFileUrl = aliOssService.upload(file);
        existing.setDocName(resolveDocName(docName, file.getOriginalFilename()));
        existing.setFileUrl(newFileUrl);
        existing.setContentHash(contentHash);
        existing.setDocumentType(documentType(file.getOriginalFilename()));
        existing.setRemark(remark);
        existing.setStatus(STATUS_SYNCING);
        existing.setUpdateTime(DateUtils.getNowDate());
        try
        {
            knowledgeDocMapper.updateKnowledgeDoc(existing);
            ragService.replaceDocument(existing);
            updateStatus(docId, STATUS_SUCCESS);
            versionService.increment();
            existing.setStatus(STATUS_SUCCESS);
        }
        catch (Exception ex)
        {
            rollbackFailedReplacement(previous, newFileUrl);
            throw new ServiceException("文档向量重建失败，已尝试恢复旧版本, docId=" + docId + ": " + ex.getMessage());
        }
        if (StringUtils.isNotEmpty(oldFileUrl) && !oldFileUrl.equals(newFileUrl))
        {
            try
            {
                aliOssService.deleteObjectByUrl(oldFileUrl);
            }
            catch (Exception cleanupEx)
            {
                log.warn("New document is active but old OSS cleanup failed, docId={}, oldUrl={}",
                    docId, oldFileUrl, cleanupEx);
            }
        }
        return existing;
    }

    @Override
    public int deleteKnowledgeDocByDocIds(Long[] docIds)
    {
        if (docIds == null || docIds.length == 0)
        {
            throw new ServiceException("待删除文档不能为空");
        }
        int successCount = 0;
        StringBuilder failures = new StringBuilder();
        for (Long docId : docIds)
        {
            try
            {
                successCount += deleteSingleKnowledgeDoc(docId);
            }
            catch (Exception ex)
            {
                if (failures.length() > 0)
                {
                    failures.append("; ");
                }
                failures.append("docId=").append(docId).append(", reason=").append(ex.getMessage());
                log.error("Knowledge document deletion failed, docId={}", docId, ex);
            }
        }
        if (failures.length() > 0)
        {
            throw new ServiceException("知识库删除存在失败项: success=" + successCount + ", " + failures);
        }
        return successCount;
    }

    @Override
    public int deleteKnowledgeDocByDocId(Long docId)
    {
        return deleteSingleKnowledgeDoc(docId);
    }

    private int deleteSingleKnowledgeDoc(Long docId)
    {
        KnowledgeDoc document = requireDocument(docId);
        ragService.deleteByDocumentId(docId);
        int rows;
        try
        {
            versionService.increment();
            rows = knowledgeDocMapper.deleteKnowledgeDocByDocId(docId);
            if (rows <= 0)
            {
                throw new IllegalStateException("数据库文档记录未删除");
            }
        }
        catch (Exception ex)
        {
            try
            {
                ragService.importOssFileToVectorStore(document);
            }
            catch (Exception restoreEx)
            {
                log.error("Failed to restore vectors after DB deletion failure, docId={}", docId, restoreEx);
            }
            if (ex instanceof RuntimeException runtimeException)
            {
                throw runtimeException;
            }
            throw new ServiceException("知识库文档删除失败, docId=" + docId + ": " + ex.getMessage());
        }
        if (StringUtils.isNotEmpty(document.getFileUrl()))
        {
            try
            {
                aliOssService.deleteObjectByUrl(document.getFileUrl());
            }
            catch (Exception cleanupEx)
            {
                log.warn("Document is deleted from DB/vector store but OSS cleanup failed, docId={}, url={}",
                    docId, document.getFileUrl(), cleanupEx);
            }
        }
        return rows;
    }

    private KnowledgeDoc requireDocument(Long docId)
    {
        if (docId == null)
        {
            throw new ServiceException("docId 不能为空");
        }
        KnowledgeDoc document = knowledgeDocMapper.selectKnowledgeDocByDocId(docId);
        if (document == null)
        {
            throw new ServiceException("知识库文档不存在, docId=" + docId);
        }
        return document;
    }

    private void validateFile(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("上传文件不能为空");
        }
        String type = documentType(file.getOriginalFilename());
        if (!ALLOWED_TYPES.contains(type))
        {
            throw new ServiceException("不支持的文档类型: " + type);
        }
    }

    private String sha256(MultipartFile file)
    {
        try (InputStream input = file.getInputStream())
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0)
            {
                if (read > 0)
                {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (Exception ex)
        {
            throw new ServiceException("计算文档内容哈希失败: " + ex.getMessage());
        }
    }

    private String documentType(String filename)
    {
        if (filename == null || !filename.contains("."))
        {
            return "unknown";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveDocName(String docName, String originalFilename)
    {
        return StringUtils.isNotEmpty(docName) ? docName.trim() : originalFilename;
    }

    private void updateStatus(Long docId, String status)
    {
        KnowledgeDoc update = new KnowledgeDoc();
        update.setDocId(docId);
        update.setStatus(status);
        update.setUpdateTime(DateUtils.getNowDate());
        knowledgeDocMapper.updateKnowledgeDoc(update);
    }

    private void rollbackFailedImport(KnowledgeDoc document)
    {
        if (document.getDocId() != null)
        {
            try
            {
                ragService.deleteByDocumentId(document.getDocId());
            }
            catch (Exception cleanupEx)
            {
                log.error("Failed to clean partial vectors after import failure, docId={}", document.getDocId(), cleanupEx);
            }
        }
        try
        {
            aliOssService.deleteObjectByUrl(document.getFileUrl());
        }
        catch (Exception cleanupEx)
        {
            log.error("Failed to clean OSS object after import failure, docId={}", document.getDocId(), cleanupEx);
        }
        if (document.getDocId() != null)
        {
            try
            {
                knowledgeDocMapper.deleteKnowledgeDocByDocId(document.getDocId());
            }
            catch (Exception cleanupEx)
            {
                log.error("Failed to clean DB record after import failure, docId={}", document.getDocId(), cleanupEx);
            }
        }
    }

    private void rollbackFailedReplacement(KnowledgeDoc previous, String newFileUrl)
    {
        try
        {
            ragService.deleteByDocumentId(previous.getDocId());
            ragService.importOssFileToVectorStore(previous);
        }
        catch (Exception restoreEx)
        {
            log.error("Failed to restore previous vectors, docId={}", previous.getDocId(), restoreEx);
        }
        try
        {
            previous.setUpdateTime(DateUtils.getNowDate());
            knowledgeDocMapper.updateKnowledgeDoc(previous);
        }
        catch (Exception restoreEx)
        {
            log.error("Failed to restore previous DB record, docId={}", previous.getDocId(), restoreEx);
        }
        try
        {
            aliOssService.deleteObjectByUrl(newFileUrl);
        }
        catch (Exception cleanupEx)
        {
            log.error("Failed to clean replacement OSS object, docId={}", previous.getDocId(), cleanupEx);
        }
    }

    private KnowledgeDoc snapshot(KnowledgeDoc source)
    {
        KnowledgeDoc target = new KnowledgeDoc();
        target.setDocId(source.getDocId());
        target.setDocName(source.getDocName());
        target.setFileUrl(source.getFileUrl());
        target.setContentHash(source.getContentHash());
        target.setDocumentType(source.getDocumentType());
        target.setStatus(source.getStatus());
        target.setRemark(source.getRemark());
        target.setCreateBy(source.getCreateBy());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateBy(source.getUpdateBy());
        target.setUpdateTime(source.getUpdateTime());
        return target;
    }
}
