package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.Student;

/**
 * 学生 Mapper 接口
 *
 * @author ruoyi
 * @date 2026-04-18
 */
public interface StudentMapper
{
    /**
     * 按学号查询学生
     *
     * @param studentId 学号
     * @return 学生
     */
    public Student selectStudentByStudentId(String studentId);

    /**
     * 按系统用户ID查询学生
     *
     * @param userId 系统用户ID
     * @return 学生
     */
    public Student selectStudentByUserId(Long userId);

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
     * 删除学生
     *
     * @param studentId 学号
     * @return 结果
     */
    public int deleteStudentByStudentId(String studentId);

    /**
     * 批量删除学生
     *
     * @param studentIds 学号集合
     * @return 结果
     */
    public int deleteStudentByStudentIds(String[] studentIds);
}
