package com.wms.simulation.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 入库仿真请求体（API-055）。
 *
 * <pre>
 * POST /api/v1/simulations/inbound
 * {
 *   "warehouseId": 1,
 *   "strategy": "smart",
 *   "items": [ { "skuId": 3, "quantity": 10 }, { "skuId": 5, "quantity": 20 } ]
 * }
 * </pre>
 *
 * @param warehouseId    目标仓库 id
 * @param strategy       入库策略标识：random / nearest / zoning / grading / smart / fifo
 * @param items          待入库明细（按数组顺序依次入库，FR-3.2）
 * @param name           方案名称，留空由服务端按「策略名-条目数」生成
 * @param distanceMetric 距离口径：manhattan（默认）/ euclidean / polyline
 * @param layerHeight    三维折线口径的单层折算高度，默认 1.0
 * @param randomSeed     随机种子，仅「随机分配」使用；留空则用固定种子保证可复现
 * @author c
 */
public record InboundSimulationRequest(
        @NotNull(message = "仓库 id 不能为空")
        Long warehouseId,

        @NotBlank(message = "入库策略不能为空")
        String strategy,

        @NotEmpty(message = "入库明细不能为空")
        @Valid
        List<InboundItemRequest> items,

        String name,

        String distanceMetric,

        Double layerHeight,

        Long randomSeed) {
}
