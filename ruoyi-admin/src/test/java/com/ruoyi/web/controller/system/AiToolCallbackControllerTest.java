package com.ruoyi.web.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.framework.security.service.AiCallbackTokenService;
import com.ruoyi.system.domain.Student;
import com.ruoyi.system.service.CurrentStudentService;
import com.ruoyi.system.service.StudentBusinessToolService;
import org.junit.jupiter.api.Test;

class AiToolCallbackControllerTest
{
    private static final String SECRET = "callback-secret-012345678901234567890123456789012345678901234567";

    @Test
    void requestStudentIdIsIgnoredAndTokenBoundStudentIsQueried()
    {
        AiCallbackTokenService tokenService = new AiCallbackTokenService(SECRET, 90);
        CurrentStudentService currentStudent = mock(CurrentStudentService.class);
        StudentBusinessToolService toolService = mock(StudentBusinessToolService.class);
        Student student = new Student();
        student.setUserId(101L);
        student.setStudentId("A001");
        when(currentStudent.requireCallbackStudent(eq(101L), eq("A001")))
            .thenReturn(student);
        when(toolService.queryScore("A001", "高数"))
            .thenReturn(Map.of("status", "SUCCESS", "message", "查询成功",
                "data", Map.of("subject", "高等数学", "score", 91)));

        AiToolCallbackController controller = new AiToolCallbackController(tokenService, currentStudent, toolService);
        String token = tokenService.issue(101L, "A001", "conversation-1");

        AjaxResult result = controller.studentScore(
            Map.of("subject", "高数", "studentId", "B002"), token, "conversation-1");

        assertEquals("SUCCESS", result.get("status"));
        verify(toolService).queryScore("A001", "高数");
    }
}
