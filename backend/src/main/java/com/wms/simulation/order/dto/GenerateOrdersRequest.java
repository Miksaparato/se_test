package com.wms.simulation.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * 随机测试订单集生成请求（API-062，FR-1.3 / C-B8）。
 *
 * <pre>
 * POST /api/v1/orders/generate
 * { "count": 50, "skuIds": [1, 2, 3], "priorityRange": [1, 5],
 *   "timeRange": ["2026-09-10", "2026-09-17"] }
 * </pre>
 *
 * @param count          生成条数（1 ~ 500，上限防止一次写入过多）
 * @param skuIds         参与随机的货物 id；留空取当前全部货物
 * @param priorityRange  优先级范围 {@code [min, max]}，默认 {@code [1, 5]}；越大越紧急
 * @param quantityRange  出库数量范围 {@code [min, max]}，默认 {@code [1, 20]}
 * @param timeRange      下达时间范围 {@code [起, 止]}（ISO 日期 {@code yyyy-MM-dd}），默认近 7 天
 * @param randomSeed     随机种子，留空则由服务端取当前时间；给定则结果可复现
 * @author c
 */
public record GenerateOrdersRequest(
        @NotNull(message = "生成条数不能为空")
        @Min(value = 1, message = "生成条数最小为 1")
        @Max(value = 500, message = "生成条数最大为 500")
        Integer count,

        List<Long> skuIds,

        List<Integer> priorityRange,

        List<Integer> quantityRange,

        List<LocalDate> timeRange,

        Long randomSeed) {
}
