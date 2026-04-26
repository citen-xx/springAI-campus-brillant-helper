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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.AiCommonQa;
import com.ruoyi.system.service.IAiCommonQaService;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.core.page.TableDataInfo;

/**
 * AI校园热点问答库Controller
 * 
 * @author ruoyi
 * @date 2026-04-15
 */
@RestController
@RequestMapping("/system/qa")
public class AiCommonQaController extends BaseController
{
    @Autowired
    private IAiCommonQaService aiCommonQaService;

    /**
     * 查询AI校园热点问答库列表
     */
    @PreAuthorize("@ss.hasPermi('system:qa:list')")
    @GetMapping("/list")
    public TableDataInfo list(AiCommonQa aiCommonQa)
    {
        startPage();
        List<AiCommonQa> list = aiCommonQaService.selectAiCommonQaList(aiCommonQa);
        return getDataTable(list);
    }

    /**
     * 导出AI校园热点问答库列表
     */
    @PreAuthorize("@ss.hasPermi('system:qa:export')")
    @Log(title = "AI校园热点问答库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, AiCommonQa aiCommonQa)
    {
        List<AiCommonQa> list = aiCommonQaService.selectAiCommonQaList(aiCommonQa);
        ExcelUtil<AiCommonQa> util = new ExcelUtil<AiCommonQa>(AiCommonQa.class);
        util.exportExcel(response, list, "AI校园热点问答库数据");
    }

    /**
     * 获取AI校园热点问答库详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:qa:query')")
    @GetMapping(value = "/{qaId}")
    public AjaxResult getInfo(@PathVariable("qaId") Long qaId)
    {
        return success(aiCommonQaService.selectAiCommonQaByQaId(qaId));
    }

    /**
     * 新增AI校园热点问答库
     */
    @PreAuthorize("@ss.hasPermi('system:qa:add')")
    @Log(title = "AI校园热点问答库", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody AiCommonQa aiCommonQa)
    {
        return toAjax(aiCommonQaService.insertAiCommonQa(aiCommonQa));
    }

    /**
     * 修改AI校园热点问答库
     */
    @PreAuthorize("@ss.hasPermi('system:qa:edit')")
    @Log(title = "AI校园热点问答库", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody AiCommonQa aiCommonQa)
    {
        return toAjax(aiCommonQaService.updateAiCommonQa(aiCommonQa));
    }

    /**
     * 删除AI校园热点问答库
     */
    @PreAuthorize("@ss.hasPermi('system:qa:remove')")
    @Log(title = "AI校园热点问答库", businessType = BusinessType.DELETE)
	@DeleteMapping("/{qaIds}")
    public AjaxResult remove(@PathVariable Long[] qaIds)
    {
        return toAjax(aiCommonQaService.deleteAiCommonQaByQaIds(qaIds));
    }
}
