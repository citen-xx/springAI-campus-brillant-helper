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

    /** 学号(12位，如2023 01 01 0001，第5-6位为专业代码) */
    private String studentId;

    /** 密码(建议存储BCrypt加密结果) */
    @Excel(name = "密码(建议存储BCrypt加密结果)")
    private String password;

    /** 学生姓名 */
    @Excel(name = "学生姓名")
    private String studentName;

    /** 专业代码(自动从学号提取) */
    @Excel(name = "专业代码(自动从学号提取)")
    private String majorCode;

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
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("studentId", getStudentId())
            .append("password", getPassword())
            .append("studentName", getStudentName())
            .append("majorCode", getMajorCode())
            .append("createTime", getCreateTime())
            .toString();
    }
}
