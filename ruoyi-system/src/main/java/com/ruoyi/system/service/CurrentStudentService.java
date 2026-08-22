package com.ruoyi.system.service;

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

    public String requireCurrentStudentId()
    {
        return requireCurrentStudent().getStudentId();
    }

    /** Resolves the token-bound identity and verifies it against the current database binding. */
    public Student requireCallbackStudent(Long userId, String tokenStudentId)
    {
        if (userId == null || StringUtils.isBlank(tokenStudentId))
        {
            throw new ServiceException("AI callback identity is invalid", HttpStatus.UNAUTHORIZED);
        }
        Student currentStudent = studentMapper.selectStudentByUserId(userId);
        if (currentStudent == null)
        {
            throw new ServiceException("当前账号未绑定学生身份", HttpStatus.FORBIDDEN);
        }
        if (!tokenStudentId.equals(currentStudent.getStudentId()))
        {
            throw new ServiceException("AI callback identity does not match current student", HttpStatus.FORBIDDEN);
        }
        return currentStudent;
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

}
