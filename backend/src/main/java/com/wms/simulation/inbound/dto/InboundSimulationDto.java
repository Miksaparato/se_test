package com.wms.simulation.inbound.dto;

import java.util.List;

/**
 * 入库仿真结果（API-055 / API-056 响应）。
 *
 * <p>{@code planId} 是该仿真产出的**库位分配方案**（落 {@code plans} 表），
 * 后续可用 API-058 对该方案做出库仿真，再用 API-051 与其他方案对比（FR-5.1）。
 *
 * @param simulationId  仿真单号，如 INB-001
 * @param planId        产出的方案 id（对外 {@code planId}）
 * @param planNo        方案编号，如 PLAN-20260910-001
 * @param strategy      策略标识
 * @param strategyName  策略中文名
 * @param warehouseId   仓库 id
 * @param assignmentCount 入库条目数
 * @param violations    重货层位违规数（软约束命中数，FR-5.1 对比指标）
 * @param totalDistance 方案布局口径总搬运路程：Σ(库位→出库口距离 × 数量)
 * @param avgDistance   布局口径平均搬运距离
 * @param hotAvgDistance 高频货平均搬运距离（周转频次 ≥ 0.5 视为高频）
 * @param assignments   逐次选位明细（含理由与告警）
 * @author c
 */
public record InboundSimulationDto(
        String simulationId,
        Long planId,
        String planNo,
        String strategy,
        String strategyName,
        Long warehouseId,
        int assignmentCount,
        int violations,
        double totalDistance,
        double avgDistance,
        double hotAvgDistance,
        List<InboundAssignment> assignments) {
}
