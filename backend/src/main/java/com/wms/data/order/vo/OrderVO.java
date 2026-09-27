package com.wms.data.order.vo;

import com.wms.domain.entity.Order;

import java.time.LocalDateTime;

/**
 * 订单视图对象（API-033 ~ API-035），字段与《接口文档》第 7 节示例一致：
 *
 * <pre>
 * { "id": 1, "orderNo": "SO-20260910-001", "skuId": 3, "quantity": 20,
 *   "priority": 4, "placedAt": "2026-09-10T09:00:00Z", "status": "pending" }
 * </pre>
 *
 * @param id       订单 id
 * @param orderNo  订单号
 * @param skuId    货物 id
 * @param skuCode  货物编码（便于前端直接展示，出库仿真可用）
 * @param quantity 出库数量
 * @param priority 订单优先级
 * @param placedAt 下达时间(UTC)
 * @param status   订单状态
 * @param remark   备注
 * @param createdAt 创建时间(UTC)
 * @author a
 */
public record OrderVO(
        Long id,
        String orderNo,
        Long skuId,
        String skuCode,
        Integer quantity,
        Integer priority,
        LocalDateTime placedAt,
        String status,
        String remark,
        LocalDateTime createdAt) {

    /**
     * 由实体构造。
     *
     * @param order   订单实体
     * @param skuCode 货物编码，可为 null
     * @return 订单视图对象
     */
    public static OrderVO from(Order order, String skuCode) {
        return new OrderVO(
                order.getId(),
                order.getOrderNo(),
                order.getSkuId(),
                skuCode,
                order.getQuantity(),
                order.getPriority(),
                order.getPlacedAt(),
                order.getStatus(),
                order.getRemark(),
                order.getCreatedAt());
    }
}
