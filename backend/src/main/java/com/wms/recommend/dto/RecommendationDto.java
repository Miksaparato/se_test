package com.wms.recommend.dto;

import com.wms.recommend.engine.LocationScore;

import java.util.List;

/** 推荐结果，对应 API-041 响应结构。 */
public record RecommendationDto(String recommendationId, SkuBrief sku,
                                List<LocationScore> candidates) {
}
