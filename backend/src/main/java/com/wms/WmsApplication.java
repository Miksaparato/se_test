package com.wms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智能仓储库位分配仿真系统 后端启动类。
 *
 * <p>本仓库为成员 b 的开发切片，仅包含 {@code recommend} 模块（库位智能推荐 + 方案对比分析）。
 * 数据访问层通过 {@code domain.repository.WarehouseDataRepository} 接口隔离，
 * 本切片内置内存实现（{@code InMemoryWarehouseDataRepository}）以便独立运行与单测；
 * 联调时由成员 a 以 MyBatis-Plus 实现同一接口（对应 COM-2 数据库契约）。</p>
 */
@SpringBootApplication
public class WmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmsApplication.class, args);
    }
}
