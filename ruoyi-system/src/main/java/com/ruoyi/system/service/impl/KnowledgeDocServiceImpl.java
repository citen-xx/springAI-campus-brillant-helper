package com.ruoyi.system.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.mapper.KnowledgeDocMapper;
import com.ruoyi.system.domain.KnowledgeDoc;
import com.ruoyi.system.service.IKnowledgeDocService;

/**
 * 校园知识库文档Service业务层处理
 * 
 * @author citen
 * @date 2026-03-24
 */
@Service
public class KnowledgeDocServiceImpl implements IKnowledgeDocService 
{
    @Autowired
    private KnowledgeDocMapper knowledgeDocMapper;

    /**
     * 查询校园知识库文档
     * 
     * @param docId 校园知识库文档主键
     * @return 校园知识库文档
     */
    @Override
    public KnowledgeDoc selectKnowledgeDocByDocId(Long docId)
    {
        return knowledgeDocMapper.selectKnowledgeDocByDocId(docId);
    }

    /**
     * 查询校园知识库文档列表
     * 
     * @param knowledgeDoc 校园知识库文档
     * @return 校园知识库文档
     */
    @Override
    public List<KnowledgeDoc> selectKnowledgeDocList(KnowledgeDoc knowledgeDoc)
    {
        return knowledgeDocMapper.selectKnowledgeDocList(knowledgeDoc);
    }

    /**
     * 新增校园知识库文档
     * 
     * @param knowledgeDoc 校园知识库文档
     * @return 结果
     */
    @Override
    public int insertKnowledgeDoc(KnowledgeDoc knowledgeDoc)
    {
        knowledgeDoc.setCreateTime(DateUtils.getNowDate());
        return knowledgeDocMapper.insertKnowledgeDoc(knowledgeDoc);
    }

    /**
     * 修改校园知识库文档
     * 
     * @param knowledgeDoc 校园知识库文档
     * @return 结果
     */
    @Override
    public int updateKnowledgeDoc(KnowledgeDoc knowledgeDoc)
    {
        knowledgeDoc.setUpdateTime(DateUtils.getNowDate());
        return knowledgeDocMapper.updateKnowledgeDoc(knowledgeDoc);
    }

    /**
     * 批量删除校园知识库文档
     * 
     * @param docIds 需要删除的校园知识库文档主键
     * @return 结果
     */
    @Override
    public int deleteKnowledgeDocByDocIds(Long[] docIds)
    {
        return knowledgeDocMapper.deleteKnowledgeDocByDocIds(docIds);
    }

    /**
     * 删除校园知识库文档信息
     * 
     * @param docId 校园知识库文档主键
     * @return 结果
     */
    @Override
    public int deleteKnowledgeDocByDocId(Long docId)
    {
        return knowledgeDocMapper.deleteKnowledgeDocByDocId(docId);
    }
}
