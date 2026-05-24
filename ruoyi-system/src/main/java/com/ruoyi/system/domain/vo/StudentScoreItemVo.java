package com.ruoyi.system.domain.vo;

public class StudentScoreItemVo
{
    private String studentId;

    private String subject;

    private Integer score;

    public StudentScoreItemVo()
    {
    }

    public StudentScoreItemVo(String studentId, String subject, Integer score)
    {
        this.studentId = studentId;
        this.subject = subject;
        this.score = score;
    }

    public String getStudentId()
    {
        return studentId;
    }

    public void setStudentId(String studentId)
    {
        this.studentId = studentId;
    }

    public String getSubject()
    {
        return subject;
    }

    public void setSubject(String subject)
    {
        this.subject = subject;
    }

    public Integer getScore()
    {
        return score;
    }

    public void setScore(Integer score)
    {
        this.score = score;
    }
}
