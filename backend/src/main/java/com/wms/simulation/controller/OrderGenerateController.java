package com.wms.simulation.controller;

import com.wms.common.ApiResponse;
import com.wms.simulation.order.RandomOrderGenerator;
import com.wms.simulation.order.dto.GenerateOrdersRequest;
import com.wms.simulation.order.dto.GenerateOrdersResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 随机测试订单集接口（API-062，FR-1.3 / C-B8）。
 *
 * <p>路径挂在 {@code /api/v1/orders/generate} 下：a 的 {@code OrderController} 负责
 * {@code /orders} 的 CRUD，并已明确把 {@code /generate} 留给 c（见其类注释），
 * 两个控制器不会产生映射冲突。
 *
 * @author c
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderGenerateController {

    private final RandomOrderGenerator randomOrderGenerator;

    /**
     * 构造控制器。
     *
     * @param randomOrderGenerator 随机订单集生成器
     */
    public OrderGenerateController(RandomOrderGenerator randomOrderGenerator) {
        this.randomOrderGenerator = randomOrderGenerator;
    }

    /**
     * API-062 生成随机测试订单集（落库为 pending 订单，可直接喂给出库仿真）。
     *
     * @param request 生成请求
     * @return 生成结果（订单 id 列表与随机种子）
     */
    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('sim:run')")
    public ApiResponse<GenerateOrdersResult> generate(
            @RequestBody @Valid GenerateOrdersRequest request) {
        return ApiResponse.ok(randomOrderGenerator.generate(request));
    }
}
