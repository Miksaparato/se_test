package com.wms.recommend.engine;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;

/**
 * 分项打分器接口（策略模式，NFR-3 可扩展性）。
 * 每个分项输出归一化到 [0,1] 的原始得分，最终得分 = Σ 权重 × 分项得分。
 */
public interface Scorer {

    /** 分项键，对应权重配置字段：weight / freq / priority / other。 */
    String key();

    /** 计算该分项的原始得分，范围 [0,1]。 */
    double score(Sku sku, Location location, ScoringContext ctx);
}
