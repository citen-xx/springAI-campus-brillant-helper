package com.ruoyi.system.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 学生对象 student
 *
 * @author ruoyi
 * @date 2026-04-18
 */
public class Student extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 绑定的系统用户ID */
    private Long userId;

    /** 学号 */
    private String studentId;

    /** 密码 */
    @Excel(name = "密码")
    private String password;

    /** 学生姓名 */
    @Excel(name = "学生姓名")
    private String studentName;

    /** 专业代码 */
    @Excel(name = "专业代码")
    private String majorCode;

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setStudentId(String studentId)
    {
        this.studentId = studentId;
    }

    public String getStudentId()
    {
        return studentId;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getPassword()
    {
        return password;
    }

    public void setStudentName(String studentName)
    {
        this.studentName = studentName;
    }

    public String getStudentName()
    {
        return studentName;
    }

    public void setMajorCode(String majorCode)
    {
        this.majorCode = majorCode;
    }

    public String getMajorCode()
    {
        return majorCode;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("userId", getUserId())
            .append("studentId", getStudentId())
            .append("password", getPassword())
            .append("studentName", getStudentName())
            .append("majorCode", getMajorCode())
            .append("createTime", getCreateTime())
            .toString();
    }
}
