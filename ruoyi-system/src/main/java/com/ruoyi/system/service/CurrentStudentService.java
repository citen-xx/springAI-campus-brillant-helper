package com.ruoyi.system.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.Student;
import com.ruoyi.system.mapper.StudentMapper;

@Service
public class CurrentStudentService
{
    public static final String TOOL_CONTEXT_STUDENT_ID = "currentStudentId";

    private final StudentMapper studentMapper;

    public CurrentStudentService(StudentMapper studentMapper)
    {
        this.studentMapper = studentMapper;
    }

    public Student requireCurrentStudent()
    {
        Long userId = requireCurrentUserId();
        Student student = studentMapper.selectStudentByUserId(userId);
        if (student == null)
        {
            throw new ServiceException("当前账号未绑定学生身份", HttpStatus.FORBIDDEN);
        }
        return student;
    }

    public Student requireCurrentStudent(ToolContext toolContext)
    {
        String studentId = extractStudentId(toolContext);
        if (StringUtils.isNotEmpty(studentId))
        {
            Student student = studentMapper.selectStudentByStudentId(studentId);
            if (student != null)
            {
                return student;
            }
        }
        return requireCurrentStudent();
    }

    public String requireCurrentStudentId()
    {
        return requireCurrentStudent().getStudentId();
    }

    public String requireCurrentStudentId(ToolContext toolContext)
    {
        return requireCurrentStudent(toolContext).getStudentId();
    }

    public Map<String, Object> buildToolContext()
    {
        Student student = requireCurrentStudent();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(TOOL_CONTEXT_STUDENT_ID, student.getStudentId());
        return context;
    }

    private Long requireCurrentUserId()
    {
        try
        {
            Long userId = SecurityUtils.getUserId();
            if (userId == null)
            {
                throw new ServiceException("当前未登录，无法查询个人数据", HttpStatus.UNAUTHORIZED);
            }
            return userId;
        }
        catch (ServiceException ex)
        {
            if (HttpStatus.UNAUTHORIZED == ex.getCode())
            {
                throw new ServiceException("当前未登录，无法查询个人数据", HttpStatus.UNAUTHORIZED);
            }
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("当前未登录，无法查询个人数据", HttpStatus.UNAUTHORIZED);
        }
    }

    private String extractStudentId(ToolContext toolContext)
    {
        if (toolContext == null || toolContext.getContext() == null)
        {
            return null;
        }
        Object studentId = toolContext.getContext().get(TOOL_CONTEXT_STUDENT_ID);
        return studentId == null ? null : studentId.toString();
    }
}
