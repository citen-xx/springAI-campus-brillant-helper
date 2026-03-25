package com.ruoyi.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 校园知识库文档对象 knowledge_doc
 * 
 * @author citen
 * @date 2026-03-24
 */
public class KnowledgeDoc extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 文档ID */
    private Long docId;

    /** 规章制度名称 */
    @Excel(name = "规章制度名称")
    private String docName;

    /** 文件存储路径 */
    @Excel(name = "文件存储路径")
    private String fileUrl;

    /** 解析状态（0未解析 1解析中 2已就绪 3解析失败） */
    @Excel(name = "解析状态", readConverterExp = "0=未解析,1=解析中,2=已就绪,3=解析失败")
    private String status;

    public void setDocId(Long docId) 
    {
        this.docId = docId;
    }

    public Long getDocId() 
    {
        return docId;
    }

    public void setDocName(String docName) 
    {
        this.docName = docName;
    }

    public String getDocName() 
    {
        return docName;
    }

    public void setFileUrl(String fileUrl) 
    {
        this.fileUrl = fileUrl;
    }

    public String getFileUrl() 
    {
        return fileUrl;
    }

    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("docId", getDocId())
            .append("docName", getDocName())
            .append("fileUrl", getFileUrl())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
