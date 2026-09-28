package com.wms.simulation.controller;

import com.wms.common.ApiResponse;
import com.wms.simulation.outbound.OutboundSimulationService;
import com.wms.simulation.outbound.dto.OutboundPath;
import com.wms.simulation.outbound.dto.OutboundSimulationDto;
import com.wms.simulation.outbound.dto.OutboundSimulationRequest;
import com.wms.simulation.outbound.dto.OutboundStats;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 出库仿真与路程统计接口（API-058 ~ API-061，FR-4）。
 *
 * <p>权限按《接口文档》第 13 节：执行仿真需 {@code sim:run}，查询记录/统计/路径需 {@code sim:view}。
 *
 * @author c
 */
@RestController
@RequestMapping("/api/v1/simulations/outbound")
public class OutboundSimulationController {

    private final OutboundSimulationService outboundSimulationService;

    /**
     * 构造控制器。
     *
     * @param outboundSimulationService 出库仿真服务
     */
    public OutboundSimulationController(OutboundSimulationService outboundSimulationService) {
        this.outboundSimulationService = outboundSimulationService;
    }

    /**
     * API-058 执行出库仿真（按单拣选）。
     *
     * @param request 仿真请求（planId 或 warehouseId、订单集、速度）
     * @return 仿真结果（统计 + 路径 + 逐单明细）
     */
    @PostMapping
    @PreAuthorize("hasAuthority('sim:run')")
    public ApiResponse<OutboundSimulationDto> simulate(
            @RequestBody @Valid OutboundSimulationRequest request) {
        return ApiResponse.ok(outboundSimulationService.simulate(request));
    }

    /**
     * API-059 查询出库仿真记录。
     *
     * @param id 仿真单号，如 OUT-001
     * @return 仿真结果
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sim:view')")
    public ApiResponse<OutboundSimulationDto> get(@PathVariable String id) {
        return ApiResponse.ok(outboundSimulationService.get(id));
    }

    /**
     * API-060 路程统计（总/平均/耗时估算）。
     *
     * @param id 仿真单号
     * @return 路程统计
     */
    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAuthority('sim:view')")
    public ApiResponse<OutboundStats> stats(@PathVariable String id) {
        return ApiResponse.ok(outboundSimulationService.stats(id));
    }

    /**
     * API-061 拣选路径坐标（供平面图绘制，FR-4.3）。
     *
     * @param id 仿真单号
     * @return 各订单的路径坐标序列
     */
    @GetMapping("/{id}/path")
    @PreAuthorize("hasAuthority('sim:view')")
    public ApiResponse<List<OutboundPath>> paths(@PathVariable String id) {
        return ApiResponse.ok(outboundSimulationService.paths(id));
    }
}
