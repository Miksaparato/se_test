package com.wms.simulation.outbound.dto;

import java.util.List;

/**
 * 出库仿真结果（API-058 / API-059 响应，FR-4.1）。
 *
 * @param simulationId 仿真单号，如 OUT-001
 * @param planId       参与仿真的方案 id；按当前真实占用拣选时为 null
 * @param planNo       方案编号；无方案时为 null
 * @param warehouseId  仓库 id
 * @param pickingMode  拣选方式（本期固定 single 按单拣选）
 * @param stockSource  库存来源说明：{@code plan} 按方案占用映射 / {@code actual} 按当前真实占用
 * @param stats        路程统计（FR-4.2）
 * @param paths        拣选路径坐标（FR-4.3，供平面图绘制）
 * @param orders       逐单拣选明细
 * @author c
 */
public record OutboundSimulationDto(
        String simulationId,
        Long planId,
        String planNo,
        Long warehouseId,
        String pickingMode,
        String stockSource,
        OutboundStats stats,
        List<OutboundPath> paths,
        List<OutboundOrderResult> orders) {
}
