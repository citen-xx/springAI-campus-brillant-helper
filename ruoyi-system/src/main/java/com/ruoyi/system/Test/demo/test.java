//package com.ruoyi.system.Test.demo;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.testng.annotations.Test;
//
//@SpringBootTest
//public class test {
//
//    @Autowired
//    private StringRedisTemplate stringRedisTemplate;
//
//    @org.testng.annotations.Test
//    public void testRedisConnection() {
//        // 1. 向 Redis 中存入一个测试键值对
//        stringRedisTemplate.opsForValue().set("test_connection_key", "Hello Redis!");
//
//        // 2. 从 Redis 中读取该键的值
//        String value = stringRedisTemplate.opsForValue().get("test_connection_key");
//
//        // 3. 打印结果以验证连接是否成功
//        System.out.println("成功连接 Redis，获取到的值为: " + value);
//    }
