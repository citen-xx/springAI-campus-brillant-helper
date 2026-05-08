package org.springframework.boot.jdbc.autoconfigure;

/**
 * 兼容桥接类
 *
 * 某些旧版 starter 仍错误引用
 * org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration，
 * 但 Spring Boot 3.x 的真实包路径已变更为
 * org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration。
 *
 * 这里提供一个空的桥接类，仅用于让旧 starter 在运行期通过类存在性检查。
 */
public class DataSourceAutoConfiguration
{
}
