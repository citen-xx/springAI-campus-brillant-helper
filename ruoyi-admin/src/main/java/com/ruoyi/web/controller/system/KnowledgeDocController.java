package com.ruoyi.web.controller.system;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.KnowledgeDoc;
import com.ruoyi.system.service.IKnowledgeDocService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.annotation.RateLimiter;
import com.ruoyi.common.enums.LimitType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * 校园知识库文档Controller
 * 
 * @author citen
 * @date 2026-03-24
 */
@RestController
@RequestMapping("/system/knowledge")
public class KnowledgeDocController extends BaseController
{
    @Autowired
    private IKnowledgeDocService knowledgeDocService;

    /**
     * 查询校园知识库文档列表
     */
    @PreAuthorize("@ss.hasPermi('system:knowledge:list')")
    @GetMapping("/list")
    public TableDataInfo list(KnowledgeDoc knowledgeDoc)
    {
        startPage();
        List<KnowledgeDoc> list = knowledgeDocService.selectKnowledgeDocList(knowledgeDoc);
        return getDataTable(list);
    }

    /**
     * 导出校园知识库文档列表
     */
    @PreAuthorize("@ss.hasPermi('system:knowledge:export')")
    @Log(title = "校园知识库文档", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, KnowledgeDoc knowledgeDoc)
    {
        List<KnowledgeDoc> list = knowledgeDocService.selectKnowledgeDocList(knowledgeDoc);
        ExcelUtil<KnowledgeDoc> util = new ExcelUtil<KnowledgeDoc>(KnowledgeDoc.class);
        util.exportExcel(response, list, "校园知识库文档数据");
    }

    /**
     * 获取校园知识库文档详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:knowledge:query')")
    @GetMapping(value = "/{docId}")
    public AjaxResult getInfo(@PathVariable("docId") Long docId)
    {
        return success(knowledgeDocService.selectKnowledgeDocByDocId(docId));
    }

    /**
     * 删除校园知识库文档
     */
    @PreAuthorize("@ss.hasPermi('system:knowledge:remove')")
    @Log(title = "校园知识库文档", businessType = BusinessType.DELETE)
	@DeleteMapping("/{docIds}")
    public AjaxResult remove(@PathVariable Long[] docIds)
    {
        return toAjax(knowledgeDocService.deleteKnowledgeDocByDocIds(docIds));
    }

    /**
     * 上传知识文档到 OSS 并自动向量化入库
     */
    @PreAuthorize("@ss.hasPermi('system:knowledge:add')")
    @RateLimiter(key = "knowledge:upload:", time = 60, count = 5, limitType = LimitType.USER_ID,
        message = "知识文档上传过于频繁，请稍后再试")
    @Log(title = "上传知识文档并向量化", businessType = BusinessType.INSERT)
    @PostMapping("/import-file")
    public AjaxResult importFile(@RequestParam("file") MultipartFile file,
        @RequestParam(value = "docName", required = false) String docName,
        @RequestParam(value = "remark", required = false) String remark)
    {
        if (file == null || file.isEmpty())
        {
            return AjaxResult.error("上传文件不能为空");
        }

        try
        {
            KnowledgeDoc knowledgeDoc = knowledgeDocService.importFile(file, docName, remark);
            AjaxResult ajaxResult = AjaxResult.success("上传并向量化成功");
            ajaxResult.put("docId", knowledgeDoc.getDocId());
            ajaxResult.put("url", knowledgeDoc.getFileUrl());
            ajaxResult.put("originalFilename", file.getOriginalFilename());
            ajaxResult.put("vectorized", true);
            return ajaxResult;
        }
        catch (Exception e)
        {
            return AjaxResult.error("上传并向量化失败: " + e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('system:knowledge:edit')")
    @RateLimiter(key = "knowledge:update:", time = 60, count = 5, limitType = LimitType.USER_ID,
        message = "知识文档更新过于频繁，请稍后再试")
    @PutMapping("/{docId}/file")
    public AjaxResult replaceFile(@PathVariable Long docId, @RequestParam("file") MultipartFile file,
        @RequestParam(value = "docName", required = false) String docName,
        @RequestParam(value = "remark", required = false) String remark)
    {
        KnowledgeDoc document = knowledgeDocService.replaceFile(docId, file, docName, remark);
        return AjaxResult.success("文档向量已重建", document);
    }
}
