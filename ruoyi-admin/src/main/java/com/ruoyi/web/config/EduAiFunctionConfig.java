package com.ruoyi.web.config;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.CampusCardMapper;
import com.ruoyi.system.mapper.StudentMapper;
import com.ruoyi.system.mapper.StudentScoreMapper;
import com.ruoyi.system.service.CurrentStudentService;

/**
 * Spring AI Function Calling 配置
 */
@Configuration
public class EduAiFunctionConfig
{
    private static final Logger log = LoggerFactory.getLogger(EduAiFunctionConfig.class);
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
    private final CurrentStudentService currentStudentService;

    public EduAiFunctionConfig(StudentMapper studentMapper, StudentScoreMapper studentScoreMapper,
        CampusCardMapper campusCardMapper, CurrentStudentService currentStudentService)
    {
        this.studentMapper = studentMapper;
        this.studentScoreMapper = studentScoreMapper;
        this.campusCardMapper = campusCardMapper;
        this.currentStudentService = currentStudentService;
    }

    @Bean(name = "getStudentScore")
    @Description("查询当前登录学生本人的课程成绩。When the user asks for score, grade, course score, 高数 or 高等数学成绩, call this tool. Never accept studentId or query another student.")
    public BiFunction<StudentScoreRequest, ToolContext, Map<String, Object>> getStudentScore()
    {
        return (request, toolContext) -> {
            if (request == null || request.subject() == null)
            {
                return toolResult("BAD_REQUEST", "课程名称不能为空");
            }

            String subject = normalizeSubject(request.subject());
            if (subject.isEmpty())
            {
                return toolResult("BAD_REQUEST", "课程名称不能为空");
            }

            try
            {
                String studentId = currentStudentService.requireCurrentStudentId(toolContext);
                log.info("Tool getStudentScore invoked, studentId={}, subject={}", studentId, subject);
                Integer score = studentScoreMapper.selectScoreByStudentIdAndSubject(studentId, subject);
                if (score == null)
                {
                    log.info("Tool getStudentScore no data, studentId={}, subject={}", studentId, subject);
                    return toolResult("NO_DATA", "未查询到当前学生该课程成绩", Map.of("subject", subject));
                }
                log.info("Tool getStudentScore success, studentId={}, subject={}, score={}", studentId, subject, score);
                return toolResult("SUCCESS", "查询成功", Map.of("subject", subject, "score", score));
            }
            catch (ServiceException ex)
            {
                log.warn("Tool getStudentScore rejected, subject={}, message={}", subject, ex.getMessage());
                return toolError(ex);
            }
        };
    }

    @Bean(name = "getCardBalance")
    @Description("查询当前登录学生本人的一卡通余额。When the user asks for campus card balance or meal card balance, call this tool. Never accept studentId or query another student.")
    public BiFunction<CardBalanceRequest, ToolContext, Map<String, Object>> getCardBalance()
    {
        return (request, toolContext) -> {
            try
            {
                String studentId = currentStudentService.requireCurrentStudentId(toolContext);
                log.info("Tool getCardBalance invoked, studentId={}", studentId);
                if (studentMapper.selectStudentByStudentId(studentId) == null)
                {
                    log.info("Tool getCardBalance no student, studentId={}", studentId);
                    return toolResult("NO_DATA", "未查询到当前学生信息");
                }
                BigDecimal balance = campusCardMapper.selectBalanceByStudentId(studentId);
                if (balance == null)
                {
                    log.info("Tool getCardBalance no data, studentId={}", studentId);
                    return toolResult("NO_DATA", "未查询到当前学生一卡通余额");
                }
                log.info("Tool getCardBalance success, studentId={}, balance={}", studentId, balance);
                return toolResult("SUCCESS", "查询成功", Map.of("balance", balance));
            }
            catch (ServiceException ex)
            {
                log.warn("Tool getCardBalance rejected, message={}", ex.getMessage());
                return toolError(ex);
            }
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

    private Map<String, Object> toolError(ServiceException ex)
    {
        if (ex.getCode() != null && ex.getCode() == HttpStatus.UNAUTHORIZED)
        {
            return toolResult("UNAUTHORIZED", ex.getMessage());
        }
        if (ex.getCode() != null && ex.getCode() == HttpStatus.FORBIDDEN)
        {
            return toolResult("UNBOUND_STUDENT", ex.getMessage());
        }
        return toolResult("ERROR", ex.getMessage());
    }

    private Map<String, Object> toolResult(String status, String message)
    {
        return toolResult(status, message, Map.of());
    }

    private Map<String, Object> toolResult(String status, String message, Map<String, Object> data)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("message", message);
        if (data != null && !data.isEmpty())
        {
            result.put("data", data);
        }
        return result;
    }
}
