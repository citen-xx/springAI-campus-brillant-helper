package com.ruoyi.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.vo.StudentScoreItemVo;

/**
 * 学生成绩 Mapper
 */
public interface StudentScoreMapper
{
    /**
     * 按学号和课程名查询成绩
     *
     * @param studentId 学号
     * @param subject 课程名
     * @return 成绩
     */
    Integer selectScoreByStudentIdAndSubject(@Param("studentId") String studentId, @Param("subject") String subject);

    /**
     * 按学号查询全部成绩
     *
     * @param studentId 学号
     * @return 成绩列表
     */
    List<StudentScoreItemVo> selectScoresByStudentId(@Param("studentId") String studentId);
}
