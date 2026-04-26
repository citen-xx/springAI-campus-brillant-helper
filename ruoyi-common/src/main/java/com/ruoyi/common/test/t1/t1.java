//package com.ruoyi.common.test.t1;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.redis.core.StringRedisTemplate;
//
//@SpringBootTest
//public class t1 {
//
//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;
//
//    @Test
//    public void testRedisConnection() {
//        // 1. 向 Redis 中存入一个测试键值对
//        stringRedisTemplate.opsForValue().set("test_connection_key", "Hello Redis!");
//
//        // 2. 从 Redis 中读取该键的值
//        String value = stringRedisTemplate.opsForValue().get("test_connection_key");
//    }}