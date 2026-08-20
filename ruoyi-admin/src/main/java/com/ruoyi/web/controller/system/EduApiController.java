package com.ruoyi.web.controller.system;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.system.mapper.CampusCardMapper;
import com.ruoyi.system.mapper.StudentScoreMapper;
import com.ruoyi.system.service.CurrentStudentService;

/**
 * 学生自助教务接口
 */
@RestController
@RequestMapping("/system/edu/api")
public class EduApiController extends BaseController
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

    private final CurrentStudentService currentStudentService;
    private final StudentScoreMapper studentScoreMapper;
    private final CampusCardMapper campusCardMapper;

    public EduApiController(CurrentStudentService currentStudentService, StudentScoreMapper studentScoreMapper,
        CampusCardMapper campusCardMapper)
    {
        this.currentStudentService = currentStudentService;
        this.studentScoreMapper = studentScoreMapper;
        this.campusCardMapper = campusCardMapper;
    }

    @PreAuthorize("@ss.hasRole('student')")
    @GetMapping("/score")
    public AjaxResult getStudentScore(@RequestParam String subject)
    {
        String normalizedSubject = normalizeSubject(subject);
        if (normalizedSubject == null)
        {
            return AjaxResult.error("无法识别课程名称，请提供高等数学、大学英语或 Java程序设计");
        }

        String studentId = currentStudentService.requireCurrentStudentId();
        Integer score = studentScoreMapper.selectScoreByStudentIdAndSubject(studentId, normalizedSubject);
        if (score == null)
        {
            return AjaxResult.error("未查询到当前学生该课程成绩");
        }
        return AjaxResult.success("查询成功", score);
    }

    @PreAuthorize("@ss.hasRole('student')")
    @GetMapping("/card/balance")
    public AjaxResult getCardBalance()
    {
        String studentId = currentStudentService.requireCurrentStudentId();
        BigDecimal balance = campusCardMapper.selectBalanceByStudentId(studentId);
        if (balance == null)
        {
            return AjaxResult.error("未查询到当前学生一卡通余额");
        }
        return AjaxResult.success("查询成功", balance);
    }

    private String normalizeSubject(String subject)
    {
        if (subject == null)
        {
            return null;
        }
        String key = subject.trim();
        if (key.isEmpty())
        {
            return null;
        }
        String lowerKey = key.toLowerCase();
        return SUBJECT_ALIAS_MAP.getOrDefault(lowerKey, SUBJECT_ALIAS_MAP.get(key));
    }
}
