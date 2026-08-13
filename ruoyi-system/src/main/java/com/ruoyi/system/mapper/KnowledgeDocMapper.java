package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.KnowledgeDoc;

/**
 * 校园知识库文档Mapper接口
 * 
 * @author citen
 * @date 2026-03-24
 */
public interface KnowledgeDocMapper 
{
    /**
     * 查询校园知识库文档
     * 
     * @param docId 校园知识库文档主键
     * @return 校园知识库文档
     */
    public KnowledgeDoc selectKnowledgeDocByDocId(Long docId);

    public KnowledgeDoc selectKnowledgeDocByContentHash(String contentHash);

    /**
     * 查询校园知识库文档列表
     * 
     * @param knowledgeDoc 校园知识库文档
     * @return 校园知识库文档集合
     */
    public List<KnowledgeDoc> selectKnowledgeDocList(KnowledgeDoc knowledgeDoc);

    /**
     * 新增校园知识库文档
     * 
     * @param knowledgeDoc 校园知识库文档
     * @return 结果
     */
    public int insertKnowledgeDoc(KnowledgeDoc knowledgeDoc);

    /**
     * 修改校园知识库文档
     * 
     * @param knowledgeDoc 校园知识库文档
     * @return 结果
     */
    public int updateKnowledgeDoc(KnowledgeDoc knowledgeDoc);

    /**
     * 删除校园知识库文档
     * 
     * @param docId 校园知识库文档主键
     * @return 结果
     */
    public int deleteKnowledgeDocByDocId(Long docId);

    /**
     * 批量删除校园知识库文档
     * 
     * @param docIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteKnowledgeDocByDocIds(Long[] docIds);
}
