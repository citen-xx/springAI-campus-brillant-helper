package com.ruoyi.web.controller.system;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.Student;
import com.ruoyi.system.domain.vo.StudentScoreItemVo;
import com.ruoyi.system.mapper.CampusCardMapper;
import com.ruoyi.system.mapper.StudentMapper;
import com.ruoyi.system.mapper.StudentScoreMapper;

@RestController
@RequestMapping("/system/admin/edu")
public class AdminEduController extends BaseController
{
    private static final Map<String, String> SUBJECT_ALIAS_MAP = new HashMap<>();

    static
    {
        SUBJECT_ALIAS_MAP.put("\u9ad8\u6570", "\u9ad8\u7b49\u6570\u5b66");
        SUBJECT_ALIAS_MAP.put("\u9ad8\u7b49\u6570\u5b66", "\u9ad8\u7b49\u6570\u5b66");
        SUBJECT_ALIAS_MAP.put("\u82f1\u8bed", "\u5927\u5b66\u82f1\u8bed");
        SUBJECT_ALIAS_MAP.put("\u5927\u5b66\u82f1\u8bed", "\u5927\u5b66\u82f1\u8bed");
        SUBJECT_ALIAS_MAP.put("java", "Java\u7a0b\u5e8f\u8bbe\u8ba1");
        SUBJECT_ALIAS_MAP.put("java\u7a0b\u5e8f\u8bbe\u8ba1", "Java\u7a0b\u5e8f\u8bbe\u8ba1");
    }

    private final StudentMapper studentMapper;
    private final StudentScoreMapper studentScoreMapper;
    private final CampusCardMapper campusCardMapper;

    public AdminEduController(StudentMapper studentMapper, StudentScoreMapper studentScoreMapper,
        CampusCardMapper campusCardMapper)
    {
        this.studentMapper = studentMapper;
        this.studentScoreMapper = studentScoreMapper;
        this.campusCardMapper = campusCardMapper;
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("/score")
    public AjaxResult getStudentScore(@RequestParam String studentId,
        @RequestParam(required = false) String subject)
    {
        if (StringUtils.isBlank(studentId))
        {
            return AjaxResult.error("studentId is required");
        }

        Student student = studentMapper.selectStudentByStudentId(studentId);
        if (student == null)
        {
            return AjaxResult.error("student not found");
        }

        Map<String, Object> data = buildStudentBase(student);
        if (StringUtils.isBlank(subject))
        {
            List<StudentScoreItemVo> scores = studentScoreMapper.selectScoresByStudentId(studentId);
            if (scores == null || scores.isEmpty())
            {
                return AjaxResult.error("score not found");
            }
            data.put("scores", scores);
            return AjaxResult.success("success", data);
        }

        String normalizedSubject = normalizeSubject(subject);
        if (StringUtils.isBlank(normalizedSubject))
        {
            return AjaxResult.error("subject is required");
        }

        Integer score = studentScoreMapper.selectScoreByStudentIdAndSubject(studentId, normalizedSubject);
        if (score == null)
        {
            return AjaxResult.error("score not found");
        }

        data.put("scores", Collections.singletonList(new StudentScoreItemVo(studentId, normalizedSubject, score)));
        return AjaxResult.success("success", data);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("/card/balance")
    public AjaxResult getCardBalance(@RequestParam String studentId)
    {
        if (StringUtils.isBlank(studentId))
        {
            return AjaxResult.error("studentId is required");
        }

        Student student = studentMapper.selectStudentByStudentId(studentId);
        if (student == null)
        {
            return AjaxResult.error("student not found");
        }

        BigDecimal balance = campusCardMapper.selectBalanceByStudentId(studentId);
        if (balance == null)
        {
            return AjaxResult.error("card balance not found");
        }

        Map<String, Object> data = buildStudentBase(student);
        data.put("balance", balance);
        return AjaxResult.success("success", data);
    }

    private Map<String, Object> buildStudentBase(Student student)
    {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("studentId", student.getStudentId());
        data.put("studentName", student.getStudentName());
        data.put("majorCode", student.getMajorCode());
        return data;
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
}
