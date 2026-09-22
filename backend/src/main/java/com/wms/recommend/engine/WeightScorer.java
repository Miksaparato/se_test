package com.wms.recommend.engine;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import org.springframework.stereotype.Component;

/**
 * 重量分项（算法 5.1 S_weight）：货物越重、库位层越低（层号小），得分越高。
 * <p>公式：S_weight = weightNorm × layerSuitability，其中
 * weightNorm = clamp(weight / maxWeightNorm)，layerSuitability = (maxLayer - layer + 1) / maxLayer。</p>
 */
@Component
public class WeightScorer implements Scorer {

    @Override
    public String key() {
        return "weight";
    }

    @Override
    public double score(Sku sku, Location location, ScoringContext ctx) {
        double weightNorm = clamp(sku.getWeight() / ctx.getRules().getMaxWeightNorm());
        double layerSuitability = ctx.layerSuitability(location.getLayer());
        return weightNorm * layerSuitability;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
