package com.wms.simulation.inbound.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 入库仿真明细项（API-055 请求的 {@code items} 元素）。
 *
 * <p>本期约定「一个库位同一时刻最多一种货物」（《需求文档》4.4 约束一），
 * 因此一项明细占用**一个**库位，{@code quantity} 记录该库位上存放的数量。
 *
 * @param skuId    货物 id
 * @param quantity 入库数量
 * @author c
 */
public record InboundItemRequest(
        @NotNull(message = "货物 id 不能为空")
        Long skuId,

        @NotNull(message = "入库数量不能为空")
        @Min(value = 1, message = "入库数量最小为 1")
        Integer quantity) {
}
