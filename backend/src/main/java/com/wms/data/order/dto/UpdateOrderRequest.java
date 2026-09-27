package com.wms.data.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 更新订单请求体（API-035）。
 *
 * <p>订单号不可修改；已出库（{@code completed}）的订单不允许再改数量。
 *
 * @param skuId    货物 id
 * @param quantity 出库数量
 * @param priority 订单优先级 1~5
 * @param placedAt 下达时间(UTC)
 * @param status   订单状态
 * @param remark   备注
 * @author a
 */
public record UpdateOrderRequest(
        Long skuId,

        @Min(value = 1, message = "出库数量最小为 1")
        Integer quantity,

        @Min(value = 1, message = "订单优先级最小为 1")
        @Max(value = 5, message = "订单优先级最大为 5")
        Integer priority,

        LocalDateTime placedAt,

        @Pattern(regexp = "^(pending|picking|completed|cancelled)$",
                message = "订单状态只能是 pending、picking、completed 或 cancelled")
        String status,

        @Size(max = 255, message = "备注长度不能超过 255 个字符")
        String remark) {
}
