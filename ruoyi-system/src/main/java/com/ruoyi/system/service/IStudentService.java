package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.Student;

/**
 * 学生Service接口
 * 
 * @author ruoyi
 * @date 2026-04-18
 */
public interface IStudentService 
{
    /**
     * 查询学生
     * 
     * @param studentId 学生主键
     * @return 学生
     */
    public Student selectStudentByStudentId(String studentId);

    /**
     * 查询学生列表
     * 
     * @param student 学生
     * @return 学生集合
     */
    public List<Student> selectStudentList(Student student);

    /**
     * 新增学生
     * 
     * @param student 学生
     * @return 结果
     */
    public int insertStudent(Student student);

    /**
     * 修改学生
     * 
     * @param student 学生
     * @return 结果
     */
    public int updateStudent(Student student);

    /**
     * 批量删除学生
     * 
     * @param studentIds 需要删除的学生主键集合
     * @return 结果
     */
    public int deleteStudentByStudentIds(String[] studentIds);

    /**
     * 删除学生信息
     * 
     * @param studentId 学生主键
     * @return 结果
     */
    public int deleteStudentByStudentId(String studentId);
}
