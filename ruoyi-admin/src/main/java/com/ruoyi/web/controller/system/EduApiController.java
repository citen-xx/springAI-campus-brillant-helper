package com.ruoyi.web.controller.system;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import org.springframework.web.bind.annotation.*;

/**
 * 提供给 Dify Agent 调用的校园教务工具接口 (Function Calling)
 */
@RestController
@RequestMapping("/system/edu/api")
@Anonymous // 允许 Dify 匿名/鉴权访问，具体看你的安全配置
public class EduApiController extends BaseController {

    /**
     * Dify 工具 1: 查询学生成绩
     * 描述：当学生问及成绩时，大模型会抽取参数并调用此接口
     */
    @GetMapping("/score")
    public AjaxResult getStudentScore(@RequestParam String studentId,
                                      @RequestParam(required = false) String subject) {
        // 真实情况：调用 Mapper 去查成绩表
        // 这里做 Mock 模拟
        if ("高数".equals(subject) || "高等数学".equals(subject)) {
            return AjaxResult.success("查询成功", 95);
        } else if ("英语".equals(subject)) {
            return AjaxResult.success("查询成功", 88);
        }
        return AjaxResult.success("查询成功", 80); // 默认分数
    }

    /**
     * Dify 工具 2: 查询一卡通余额
     */
    @GetMapping("/card/balance")
    public AjaxResult getCardBalance(@RequestParam String studentId) {
        // 模拟查表返回余额
        return AjaxResult.success("查询成功", 128.50);
    }
}
