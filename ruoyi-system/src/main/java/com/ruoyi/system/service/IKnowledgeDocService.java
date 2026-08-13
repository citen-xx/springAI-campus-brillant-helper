package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.KnowledgeDoc;
import org.springframework.web.multipart.MultipartFile;

public interface IKnowledgeDocService
{
    public KnowledgeDoc selectKnowledgeDocByDocId(Long docId);

    public List<KnowledgeDoc> selectKnowledgeDocList(KnowledgeDoc knowledgeDoc);

    public KnowledgeDoc importFile(MultipartFile file, String docName, String remark);

    public KnowledgeDoc replaceFile(Long docId, MultipartFile file, String docName, String remark);

    public int deleteKnowledgeDocByDocIds(Long[] docIds);

    public int deleteKnowledgeDocByDocId(Long docId);
}
