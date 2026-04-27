package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.KnowledgeDoc;

public interface IKnowledgeDocService
{
    public KnowledgeDoc selectKnowledgeDocByDocId(Long docId);

    public List<KnowledgeDoc> selectKnowledgeDocList(KnowledgeDoc knowledgeDoc);

    public int insertKnowledgeDoc(KnowledgeDoc knowledgeDoc);

    public void asyncUploadToDifyEngine(Long docId);

    public int updateKnowledgeDoc(KnowledgeDoc knowledgeDoc);

    public int deleteKnowledgeDocByDocIds(Long[] docIds);

    public int deleteKnowledgeDocByDocId(Long docId);
}
