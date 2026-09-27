package com.wms.data.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 创建出库订单请求体（API-034）。
 *
 * @param orderNo   订单号，全局唯一；留空由服务端生成（{@code SO-yyyyMMdd-序号}）
 * @param skuId     货物 id
 * @param quantity  出库数量
 * @param priority  订单优先级 1~5，越大越紧急（出库仿真按其降序排序）
 * @param placedAt  下达时间(UTC)；留空取当前时间
 * @param status    订单状态，缺省 pending
 * @param remark    备注
 * @author a
 */
public record CreateOrderRequest(
        @Size(max = 40, message = "订单号长度不能超过 40 个字符")
        @Pattern(regexp = "^$|^[A-Za-z0-9_-]+$", message = "订单号只能包含字母、数字、下划线、连字符")
        String orderNo,

        @NotNull(message = "货物 id 不能为空")
        Long skuId,

        @NotNull(message = "出库数量不能为空")
        @Min(value = 1, message = "出库数量最小为 1")
        Integer quantity,

        @NotNull(message = "订单优先级不能为空")
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
