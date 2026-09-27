package com.wms.recommend.config.dto;

import com.wms.recommend.engine.LocationScore;
import com.wms.recommend.engine.ScoreWeightConfig;
import com.wms.recommend.engine.StorageRuleConfig;

import java.util.List;

/** 参数校准结果，对应 API-048 响应。 */
public record CalibrateResult(ScoreWeightConfig weights, StorageRuleConfig rules,
                              List<LocationScore> candidates) {
}
