package com.wms.simulation.inbound.dto;

/**
 * 一次入库选位明细（API-055 响应的 {@code assignments} 元素）。
 *
 * <p>对应 FR-3.2「记录每次入库选择的库位与**理由**」：{@code reason} 与 {@code warning}
 * 都由策略生成，可直接展示给使用者，也是「带评分与理由的推荐排序」在仿真侧的体现。
 *
 * @param skuId      货物 id
 * @param skuCode    货物编码
 * @param skuName    货物名称
 * @param locationId 分配到的库位 id
 * @param code       库位编码，如 A-01-03-02
 * @param quantity   在该库位存放的数量
 * @param score      策略适配分（无评分语义的策略为 0）
 * @param reason     选择理由（中文）
 * @param warning    软约束告警（如重货层高违规），无告警时为 null
 * @author c
 */
public record InboundAssignment(
        Long skuId,
        String skuCode,
        String skuName,
        Long locationId,
        String code,
        Integer quantity,
        double score,
        String reason,
        String warning) {
}
