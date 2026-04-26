package com.ruoyi.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * AI校园热点问答库对象 ai_common_qa
 * 
 * @author ruoyi
 * @date 2026-04-15
 */

public class AiCommonQa extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long qaId;

    /** 标准问题描述 */
    @Excel(name = "标准问题描述")
    private String question;

    /** 标准答案内容(支持Markdown) */
    @Excel(name = "标准答案内容(支持Markdown)")
    private String answer;

    /** 分类(如：教务、生活、奖学金) */
    @Excel(name = "分类(如：教务、生活、奖学金)")
    private String category;

    /** 关键词标签(逗号分隔，用于精准匹配) */
    @Excel(name = "关键词标签(逗号分隔，用于精准匹配)")
    private String keywords;

    /** 累计查询次数 */
    @Excel(name = "累计查询次数")
    private Long hitCount;

    /** 是否设为常驻热门(0否 1是，1的话永不过期) */
    @Excel(name = "是否设为常驻热门(0否 1是，1的话永不过期)")
    private String isHot;

    /** 自定义缓存有效时间(秒) */
    @Excel(name = "自定义缓存有效时间(秒)")
    private Long cacheTtl;

    /** 状态(0正常 1停用) */
    @Excel(name = "状态(0正常 1停用)")
    private String status;

    public void setQaId(Long qaId) 
    {
        this.qaId = qaId;
    }

    public Long getQaId() 
    {
        return qaId;
    }

    public void setQuestion(String question) 
    {
        this.question = question;
    }

    public String getQuestion() 
    {
        return question;
    }

    public void setAnswer(String answer) 
    {
        this.answer = answer;
    }

    public String getAnswer() 
    {
        return answer;
    }

    public void setCategory(String category) 
    {
        this.category = category;
    }

    public String getCategory() 
    {
        return category;
    }

    public void setKeywords(String keywords) 
    {
        this.keywords = keywords;
    }

    public String getKeywords() 
    {
        return keywords;
    }

    public void setHitCount(Long hitCount) 
    {
        this.hitCount = hitCount;
    }

    public Long getHitCount() 
    {
        return hitCount;
    }

    public void setIsHot(String isHot) 
    {
        this.isHot = isHot;
    }

    public String getIsHot() 
    {
        return isHot;
    }

    public void setCacheTtl(Long cacheTtl) 
    {
        this.cacheTtl = cacheTtl;
    }

    public Long getCacheTtl() 
    {
        return cacheTtl;
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
            .append("qaId", getQaId())
            .append("question", getQuestion())
            .append("answer", getAnswer())
            .append("category", getCategory())
            .append("keywords", getKeywords())
            .append("hitCount", getHitCount())
            .append("isHot", getIsHot())
            .append("cacheTtl", getCacheTtl())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
