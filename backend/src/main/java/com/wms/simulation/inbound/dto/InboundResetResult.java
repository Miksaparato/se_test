package com.wms.simulation.inbound.dto;

/**
 * 入库仿真「清空重置」结果（API-057 响应）。
 *
 * @param simulationId  被重置的仿真单号
 * @param removedPlanId 一并清除的方案 id；该仿真未产出方案时为 null
 * @param reset         是否重置成功
 * @author c
 */
public record InboundResetResult(String simulationId, Long removedPlanId, boolean reset) {
}
