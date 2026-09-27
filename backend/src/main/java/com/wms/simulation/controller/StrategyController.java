package com.wms.simulation.controller;

import com.wms.common.ApiResponse;
import com.wms.simulation.dto.StrategyInfo;
import com.wms.simulation.strategy.StrategyRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 入库策略清单接口（API-054）。
 *
 * <p>策略为插件式实现（C-B1），本接口把注册表里的全部策略吐给前端下拉框，
 * 新增策略后无需改动前端即可出现在列表里（NFR-3）。
 *
 * @author c
 */
@RestController
@RequestMapping("/api/v1/strategies")
public class StrategyController {

    private final StrategyRegistry strategyRegistry;

    /**
     * 构造控制器。
     *
     * @param strategyRegistry 策略注册表
     */
    public StrategyController(StrategyRegistry strategyRegistry) {
        this.strategyRegistry = strategyRegistry;
    }

    /**
     * API-054 内置入库策略列表（随机/就近/分区/分级/智能推荐/FIFO）。
     *
     * @return 策略列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('sim:run')")
    public ApiResponse<List<StrategyInfo>> list() {
        return ApiResponse.ok(strategyRegistry.list().stream()
                .map(strategy -> new StrategyInfo(strategy.name(), strategy.displayName(), strategy.description()))
                .toList());
    }
}
