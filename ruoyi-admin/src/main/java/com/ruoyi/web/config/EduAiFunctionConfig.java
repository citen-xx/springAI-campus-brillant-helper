package com.ruoyi.web.config;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import com.ruoyi.system.mapper.CampusCardMapper;
import com.ruoyi.system.mapper.StudentMapper;
import com.ruoyi.system.mapper.StudentScoreMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Function Calling 配置
 */
@Configuration
public class EduAiFunctionConfig
{
    private static final Map<String, String> SUBJECT_ALIAS_MAP = new HashMap<>();

    static
    {
        SUBJECT_ALIAS_MAP.put("高数", "高等数学");
        SUBJECT_ALIAS_MAP.put("高等数学", "高等数学");
        SUBJECT_ALIAS_MAP.put("英语", "大学英语");
        SUBJECT_ALIAS_MAP.put("大学英语", "大学英语");
        SUBJECT_ALIAS_MAP.put("java", "Java程序设计");
        SUBJECT_ALIAS_MAP.put("java程序设计", "Java程序设计");
    }

    private final StudentMapper studentMapper;
    private final StudentScoreMapper studentScoreMapper;
    private final CampusCardMapper campusCardMapper;

    public EduAiFunctionConfig(StudentMapper studentMapper, StudentScoreMapper studentScoreMapper, CampusCardMapper campusCardMapper)
    {
        this.studentMapper = studentMapper;
        this.studentScoreMapper = studentScoreMapper;
        this.campusCardMapper = campusCardMapper;
    }

    /**
     * 查询学生成绩
     */
    @Bean(name = "getStudentScore")
    @Description("查询指定学生的某门课程成绩")
    public Function<StudentScoreRequest, Integer> getStudentScore()
    {
        return request -> {
            if (request == null || request.studentId() == null || request.subject() == null)
            {
                return 0;
            }

            String studentId = request.studentId().trim();
            String subject = normalizeSubject(request.subject());

            if (studentMapper.selectStudentByStudentId(studentId) == null)
            {
                return 0;
            }

            Integer score = studentScoreMapper.selectScoreByStudentIdAndSubject(studentId, subject);
            return score == null ? 0 : score;
        };
    }

    /**
     * 查询一卡通余额
     */
    @Bean(name = "getCardBalance")
    @Description("查询指定学生的一卡通余额")
    public Function<CardBalanceRequest, BigDecimal> getCardBalance()
    {
        return request -> {
            if (request == null || request.studentId() == null)
            {
                return BigDecimal.ZERO;
            }

            String studentId = request.studentId().trim();

            if (studentMapper.selectStudentByStudentId(studentId) == null)
            {
                return BigDecimal.ZERO;
            }

            BigDecimal balance = campusCardMapper.selectBalanceByStudentId(studentId);
            return balance == null ? BigDecimal.ZERO : balance;
        };
    }

    private String normalizeSubject(String subject)
    {
        if (subject == null)
        {
            return "";
        }

        String key = subject.trim();
        if (key.isEmpty())
        {
            return key;
        }

        String lowerKey = key.toLowerCase();
        return SUBJECT_ALIAS_MAP.getOrDefault(lowerKey, SUBJECT_ALIAS_MAP.getOrDefault(key, key));
    }

    /**
     * 成绩查询入参
     */
    public record StudentScoreRequest(String studentId, String subject)
    {
    }

    /**
     * 一卡通查询入参
     */
    public record CardBalanceRequest(String studentId)
    {
    }
}
