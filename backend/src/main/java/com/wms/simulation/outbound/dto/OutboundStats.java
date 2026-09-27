package com.wms.simulation.outbound.dto;

import java.util.List;

/**
 * 出库仿真路程统计（API-060 响应，FR-4.2 / C-B6）。
 *
 * <p>口径（COM-6，由 c 统一）：
 * <ul>
 *   <li>{@code orderDistances}：每张订单的搬运路程 = 该订单各取货库位到出库口的单程之和；</li>
 *   <li>{@code totalDistance}：全部订单搬运路程之和（总搬运路程）；</li>
 *   <li>{@code avgDistance}：平均每单路程 = 总路程 ÷ 订单数；</li>
 *   <li>{@code estimatedTime}：耗时估算（秒）= 总路程 ÷ 平均移动速度。</li>
 * </ul>
 *
 * @param totalDistance  总搬运路程
 * @param orderDistances 各订单路程（与 {@code orders} 顺序一致）
 * @param avgDistance    平均每单路程
 * @param estimatedTime  出库耗时估算（秒）
 * @param orderCount     参与仿真的订单数
 * @param hotAvgDistance 高频货平均搬运距离（周转频次 ≥ 0.5 视为高频，FR-5.1 对比指标）
 * @param speed          平均移动速度（格/秒）
 * @param distanceMetric 距离口径标识
 * @author c
 */
public record OutboundStats(
        double totalDistance,
        List<Double> orderDistances,
        double avgDistance,
        double estimatedTime,
        int orderCount,
        double hotAvgDistance,
        double speed,
        String distanceMetric) {
}
