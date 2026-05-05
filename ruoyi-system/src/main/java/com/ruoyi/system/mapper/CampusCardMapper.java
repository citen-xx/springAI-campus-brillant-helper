package com.ruoyi.system.mapper;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

/**
 * 一卡通账户 Mapper
 */
public interface CampusCardMapper
{
    /**
     * 按学号查询一卡通余额
     *
     * @param studentId 学号
     * @return 余额
     */
    BigDecimal selectBalanceByStudentId(@Param("studentId") String studentId);
}
