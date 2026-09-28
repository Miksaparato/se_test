package com.wms.simulation.controller;

import com.wms.common.ApiResponse;
import com.wms.simulation.inbound.InboundSimulationService;
import com.wms.simulation.inbound.dto.InboundResetResult;
import com.wms.simulation.inbound.dto.InboundSimulationDto;
import com.wms.simulation.inbound.dto.InboundSimulationRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 入库策略仿真接口（API-055 ~ API-057，FR-3）。
 *
 * <p>权限按《接口文档》第 12 节：执行仿真与重置需 {@code sim:run}，查询仿真记录需 {@code sim:view}。
 *
 * @author c
 */
@RestController
@RequestMapping("/api/v1/simulations/inbound")
public class InboundSimulationController {

    private final InboundSimulationService inboundSimulationService;

    /**
     * 构造控制器。
     *
     * @param inboundSimulationService 入库仿真服务
     */
    public InboundSimulationController(InboundSimulationService inboundSimulationService) {
        this.inboundSimulationService = inboundSimulationService;
    }

    /**
     * API-055 执行入库仿真，形成库位分配方案。
     *
     * @param request 仿真请求（仓库、策略、入库明细）
     * @return 仿真结果（含方案 id 与逐次选位理由）
     */
    @PostMapping
    @PreAuthorize("hasAuthority('sim:run')")
    public ApiResponse<InboundSimulationDto> simulate(
            @RequestBody @Valid InboundSimulationRequest request) {
        return ApiResponse.ok(inboundSimulationService.simulate(request));
    }

    /**
     * API-056 查询入库仿真记录与选位理由。
     *
     * @param id 仿真单号，如 INB-001
     * @return 仿真结果
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sim:view')")
    public ApiResponse<InboundSimulationDto> get(@PathVariable String id) {
        return ApiResponse.ok(inboundSimulationService.get(id));
    }

    /**
     * API-057 清空重置，换策略重新仿真。
     *
     * @param id 仿真单号
     * @return 重置结果（含被清除的方案 id）
     */
    @PostMapping("/{id}/reset")
    @PreAuthorize("hasAuthority('sim:run')")
    public ApiResponse<InboundResetResult> reset(@PathVariable String id) {
        return ApiResponse.ok(inboundSimulationService.reset(id));
    }
}
