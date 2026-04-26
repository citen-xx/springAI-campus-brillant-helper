package com.ruoyi.system.service;

import java.util.List;
import com.ruoyi.system.domain.AiCommonQa;

/**
 * AI校园热点问答库Service接口
 * 
 * @author ruoyi
 * @date 2026-04-15
 */
public interface IAiCommonQaService 
{
    /**
     * 查询AI校园热点问答库
     * 
     * @param qaId AI校园热点问答库主键
     * @return AI校园热点问答库
     */
    public AiCommonQa selectAiCommonQaByQaId(Long qaId);

    /**
     * 查询AI校园热点问答库列表
     * 
     * @param aiCommonQa AI校园热点问答库
     * @return AI校园热点问答库集合
     */
    public List<AiCommonQa> selectAiCommonQaList(AiCommonQa aiCommonQa);

    /**
     * 新增AI校园热点问答库
     * 
     * @param aiCommonQa AI校园热点问答库
     * @return 结果
     */
    public int insertAiCommonQa(AiCommonQa aiCommonQa);

    /**
     * 修改AI校园热点问答库
     * 
     * @param aiCommonQa AI校园热点问答库
     * @return 结果
     */
    public int updateAiCommonQa(AiCommonQa aiCommonQa);

    /**
     * 批量删除AI校园热点问答库
     * 
     * @param qaIds 需要删除的AI校园热点问答库主键集合
     * @return 结果
     */
    public int deleteAiCommonQaByQaIds(Long[] qaIds);

    /**
     * 删除AI校园热点问答库信息
     * 
     * @param qaId AI校园热点问答库主键
     * @return 结果
     */
    public int deleteAiCommonQaByQaId(Long qaId);
}
