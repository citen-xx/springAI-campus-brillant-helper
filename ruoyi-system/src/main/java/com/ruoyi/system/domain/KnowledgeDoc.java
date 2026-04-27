package com.ruoyi.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

public class KnowledgeDoc extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long docId;

    @Excel(name = "docName")
    private String docName;

    @Excel(name = "fileUrl")
    private String fileUrl;

    @Excel(name = "syncStatus", readConverterExp = "0=pending,1=syncing,2=success")
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

    // Keep compatibility with the requested syncStatus naming.
    public void setSyncStatus(String syncStatus)
    {
        this.status = syncStatus;
    }

    public String getSyncStatus()
    {
        return status;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("docId", getDocId())
            .append("docName", getDocName())
            .append("fileUrl", getFileUrl())
            .append("syncStatus", getSyncStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
