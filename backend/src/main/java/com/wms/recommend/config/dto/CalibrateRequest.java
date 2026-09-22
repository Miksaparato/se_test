package com.wms.recommend.config.dto;

import com.wms.recommend.engine.ScoreWeightConfig;
import com.wms.recommend.engine.StorageRuleConfig;
import jakarta.validation.constraints.NotNull;

/** 参数校准请求，对应 API-048（B-B9 调参重新评分）。权重/规则可空，空则用当前配置。 */
public record CalibrateRequest(
        @NotNull(message = "skuId 不能为空") Long skuId,
        @NotNull(message = "warehouseId 不能为空") Long warehouseId,
        Integer topN,
        ScoreWeightConfig weights,
        StorageRuleConfig rules) {
}
