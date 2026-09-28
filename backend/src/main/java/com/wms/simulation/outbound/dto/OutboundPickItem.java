package com.wms.simulation.outbound.dto;

/**
 * 单条订单的一次取货明细（API-059 响应的 {@code orders[].picks} 元素）。
 *
 * <p>本期一张订单只含一条 SKU 明细，但该 SKU 可能分布在多个库位（一库位一货物），
 * 因此按「距出库口从近到远」依次取货，直到凑够订单数量。
 *
 * @param locationId 库位 id
 * @param code       库位编码
 * @param quantity   从该库位取走的数量
 * @param distance   该库位到出库口的搬运距离（移动路程的组成项）
 * @author c
 */
public record OutboundPickItem(Long locationId, String code, Integer quantity, double distance) {
}
