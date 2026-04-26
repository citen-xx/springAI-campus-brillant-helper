package com.ruoyi.system.service.impl;

import java.util.List;
import java.util.concurrent.TimeUnit;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.ruoyi.system.mapper.AiCommonQaMapper;
import com.ruoyi.system.domain.AiCommonQa;
import com.ruoyi.system.service.IAiCommonQaService;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI校园热点问答库Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-04-15
 */
@Service
public class AiCommonQaServiceImpl implements IAiCommonQaService 
{
    @Autowired
    private AiCommonQaMapper aiCommonQaMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 查询AI校园热点问答库
     * 
     * @param qaId AI校园热点问答库主键
     * @return AI校园热点问答库
     */
    @Override
    public AiCommonQa selectAiCommonQaByQaId(Long qaId)
    {
        String key = "ai:common:qa:list:" + qaId;

        // 【第一重检查】先查缓存
        String rulesString = stringRedisTemplate.opsForValue().get(key);
        if(StringUtils.isNotBlank(rulesString)){
            return JSONUtil.toBean(rulesString, AiCommonQa.class);
        }
        if(rulesString != null){
            return null; // 命中缓存穿透机制的空字符串
        }

        // 缓存未命中，需要查库，使用分布式锁防止击穿
        String lockKey = "lock:ai:common:qa:" + qaId;
        RLock lock = redissonClient.getLock(lockKey);

        AiCommonQa aiCommonQa = null;
        try {
            // 尝试获取锁，等待3秒，持有10秒
            boolean isLocked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (isLocked) {
                // 【第二重检查】获取到锁后再查一次缓存
                rulesString = stringRedisTemplate.opsForValue().get(key);
                if(StringUtils.isNotBlank(rulesString)){
                    return JSONUtil.toBean(rulesString, AiCommonQa.class);
                }
                if(rulesString != null){
                    return null;
                }

                // 查库
                aiCommonQa = aiCommonQaMapper.selectAiCommonQaByQaId(qaId);
                if(aiCommonQa == null){
                    // 解决穿透问题：查不到数据时，将空字符串写入缓存，并设置较短的过期时间
                    stringRedisTemplate.opsForValue().set(key, "", 20, TimeUnit.MINUTES);
                } else {
                    // 重建缓存并加入随机过期时间，防止雪崩
                    int randomMinutes = 30 + new java.util.Random().nextInt(10);
                    stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(aiCommonQa), randomMinutes, TimeUnit.MINUTES);
                }
            } else {
                // 没拿到锁，睡眠一会后重试
                Thread.sleep(100);
                return selectAiCommonQaByQaId(qaId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁中断", e);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        return aiCommonQa;
    }

    /**
     * 查询AI校园热点问答库列表
     * 
     * @param aiCommonQa AI校园热点问答库
     * @return AI校园热点问答库
     */
    @Override
    public List<AiCommonQa> selectAiCommonQaList(AiCommonQa aiCommonQa)
    {
        // 生成缓存键，基于查询条件生成唯一标识
        String cacheKey = generateCacheKey(aiCommonQa);
        
        // 首先尝试从 Redis 缓存中获取
        String cacheData = stringRedisTemplate.opsForValue().get(cacheKey);
        if(StringUtils.isNotBlank(cacheData)){
            // 使用 FastJSON 解析缓存数据
            return JSON.parseArray(cacheData, AiCommonQa.class);
        }
        if(cacheData != null){
            // 缓存中存在空字符串（命中防穿透缓存），直接返回空集合，不再查询数据库
            return new java.util.ArrayList<>();
        }

        // 如果缓存中没有，则从数据库查询
        List<AiCommonQa> aiCommonQaList = aiCommonQaMapper.selectAiCommonQaList(aiCommonQa);
        
        // 将查询结果存入 Redis 缓存
        if(aiCommonQaList != null && !aiCommonQaList.isEmpty()){
            stringRedisTemplate.opsForValue().set(cacheKey, 
                JSON.toJSONString(aiCommonQaList), 30, java.util.concurrent.TimeUnit.MINUTES);
        }else{
            stringRedisTemplate.opsForValue().set(cacheKey,"",20, TimeUnit.MINUTES);
        }
        
        return aiCommonQaList;
    }
    
    /**
     * 生成缓存键
     * 
     * @param aiCommonQa 查询条件
     * @return 缓存键
     */
    private String generateCacheKey(AiCommonQa aiCommonQa) {
        StringBuilder keyBuilder = new StringBuilder("ai:common:qa:list:");
        
        // 基于查询条件生成唯一键
        if (aiCommonQa != null) {
            if (aiCommonQa.getQaId() != null) {
                keyBuilder.append("id:").append(aiCommonQa.getQaId());
            }
            if (StringUtils.isNotBlank(aiCommonQa.getQuestion())) {
                keyBuilder.append(":q:").append(aiCommonQa.getQuestion().hashCode());
            }
            if (StringUtils.isNotBlank(aiCommonQa.getAnswer())) {
                keyBuilder.append(":a:").append(aiCommonQa.getAnswer().hashCode());
            }
            if (StringUtils.isNotBlank(aiCommonQa.getStatus())) {
                keyBuilder.append(":s:").append(aiCommonQa.getStatus());
            }
        }
        
        // 如果没有任何查询条件，使用默认键
        if (keyBuilder.length() == "ai:common:qa:list:".length()) {
            keyBuilder.append("all");
        }
        
        return keyBuilder.toString();
    }

    /**
     * 清理所有相关缓存
     */
    private void clearCache() {
        java.util.Set<String> keys = stringRedisTemplate.keys("ai:common:qa:list:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    /**
     * 新增AI校园热点问答库
     * 
     * @param aiCommonQa AI校园热点问答库
     * @return 结果
     */
    @Override
    public int insertAiCommonQa(AiCommonQa aiCommonQa)
    {
        aiCommonQa.setCreateTime(DateUtils.getNowDate());
        int rows = aiCommonQaMapper.insertAiCommonQa(aiCommonQa);
        clearCache();
        return rows;
    }

    /**
     * 修改AI校园热点问答库
     * 
     * @param aiCommonQa AI校园热点问答库
     * @return 结果
     */
    @Override
    @Transactional
    public int updateAiCommonQa(AiCommonQa aiCommonQa)
    {
        Long id=aiCommonQa.getQaId();

        if(id==null){
            return 0;
        }

        aiCommonQa.setUpdateTime(DateUtils.getNowDate());

        int rows = aiCommonQaMapper.updateAiCommonQa(aiCommonQa);
        clearCache();
        return rows;
    }

    /**
     * 批量删除AI校园热点问答库
     * 
     * @param qaIds 需要删除的AI校园热点问答库主键
     * @return 结果
     */
    @Override
    public int deleteAiCommonQaByQaIds(Long[] qaIds)
    {
        int rows = aiCommonQaMapper.deleteAiCommonQaByQaIds(qaIds);
        clearCache();
        return rows;
    }

    /**
     * 删除AI校园热点问答库信息
     * 
     * @param qaId AI校园热点问答库主键
     * @return 结果
     */
    @Override
    public int deleteAiCommonQaByQaId(Long qaId)
    {
        int rows = aiCommonQaMapper.deleteAiCommonQaByQaId(qaId);
        clearCache();
        return rows;
    }
}
