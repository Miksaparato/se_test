package com.wms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 小型智能仓储库位分配仿真系统 后端启动类。
 *
 * <p>技术栈：Java 17 + Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + Flyway（《代码规范》V1.1 第 1 节）。
 *
 * @author a
 */
@SpringBootApplication
@EnableTransactionManagement
public class WmsApplication {

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }
}
