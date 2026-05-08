package org.springframework.boot.jdbc.autoconfigure;

/**
 * 兼容桥接类
 *
 * 旧版 starter 错误引用了
 * org.springframework.boot.jdbc.autoconfigure.DataSourceProperties，
 * 而 Spring Boot 3.x 正确类位于
 * org.springframework.boot.autoconfigure.jdbc.DataSourceProperties。
 *
 * 这里继承新类，兼容旧依赖在运行期的反射解析。
 */
public class DataSourceProperties extends org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
{
}
