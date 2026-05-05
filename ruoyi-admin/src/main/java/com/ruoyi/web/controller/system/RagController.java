package com.ruoyi.web.controller.system;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.Oss.AliOssService;
import com.ruoyi.system.service.RagService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * RAG 知识库导入控制器
 *
 * 负责：
 * 1. 接收前端文件
 * 2. 上传阿里云 OSS
 * 3. 自动向量化入库
 */
@RestController
@RequestMapping("/system/rag")
public class RagController extends BaseController
{
    @Resource
    private AliOssService aliOssService;

    @Resource
    private RagService ragService;

    /**
     * 上传文件到 OSS 并自动向量化入库
     *
     * @param file 文件
     * @return OSS 地址 + 入库结果
     */
    @Log(title = "RAG知识库导入", businessType = BusinessType.INSERT)
    @PostMapping("/upload-and-import")
    public AjaxResult uploadAndImport(@RequestParam("file") MultipartFile file)
    {
        try
        {
            // 1. 上传到 OSS
            String fileUrl = aliOssService.upload(file);

            // 2. 自动向量化入库
            ragService.importOssFileToVectorStore(fileUrl);

            AjaxResult ajaxResult = AjaxResult.success("上传并向量化成功");
            ajaxResult.put("url", fileUrl);
            ajaxResult.put("originalFilename", file.getOriginalFilename());
            ajaxResult.put("vectorized", true);
            return ajaxResult;
        }
        catch (Exception e)
        {
            return AjaxResult.error("上传并向量化失败: " + e.getMessage());
        }
    }
}
