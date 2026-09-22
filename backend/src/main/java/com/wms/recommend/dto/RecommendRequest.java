package com.wms.recommend.dto;

import jakarta.validation.constraints.NotNull;

/** 推荐请求，对应 API-041 请求体。 */
public record RecommendRequest(
        @NotNull(message = "skuId 不能为空") Long skuId,
        @NotNull(message = "warehouseId 不能为空") Long warehouseId,
        Integer topN) {
}
