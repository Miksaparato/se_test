package com.wms.simulation.outbound.dto;

/**
 * 出库仿真请求体（API-058）。
 *
 * <pre>
 * POST /api/v1/simulations/outbound
 * { "planId": 12, "orderIds": [1, 2, 3], "pickingMode": "single", "speed": 1.5 }
 * </pre>
 *
 * @param planId         分配方案 id；给定则按方案的 {@code location_map} 还原库存布局做拣选
 * @param warehouseId    仓库 id；不传 {@code planId} 时必填，按当前真实占用情况拣选
 * @param orderIds       参与仿真的订单 id；留空则取全部 {@code pending} 订单
 * @param pickingMode    拣选方式：{@code single} 按单拣选（本期唯一实现，缺省值）
 * @param speed          平均移动速度（格/秒），用于耗时估算，缺省 1.0；非法值按 1.0
 * @param distanceMetric 距离口径：manhattan（默认）/ euclidean / polyline
 * @param layerHeight    三维折线口径的单层折算高度，默认 1.0
 * @author c
 */
public record OutboundSimulationRequest(
        Long planId,
        Long warehouseId,
        java.util.List<Long> orderIds,
        String pickingMode,
        Double speed,
        String distanceMetric,
        Double layerHeight) {
}
