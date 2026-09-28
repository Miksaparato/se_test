package com.wms.simulation.outbound.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单条订单的出库仿真结果（API-059 响应的 {@code orders} 元素）。
 *
 * @param orderId   订单 id
 * @param orderNo   订单号
 * @param skuId     货物 id
 * @param skuCode   货物编码
 * @param quantity  订单数量
 * @param priority  订单优先级（越大越紧急，FR-4.1 排序依据）
 * @param placedAt  下达时间（同优先级时按此升序）
 * @param picks     取货明细（按库位到出库口由近及远）
 * @param distance  该订单的搬运路程（各取货库位到出库口的单程之和）
 * @param warning   告警（如「货物不在任何库位」「库存不足」），无告警时为 null
 * @author c
 */
public record OutboundOrderResult(
        Long orderId,
        String orderNo,
        Long skuId,
        String skuCode,
        Integer quantity,
        Integer priority,
        LocalDateTime placedAt,
        List<OutboundPickItem> picks,
        double distance,
        String warning) {
}
