package com.wms.recommend.engine;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import org.springframework.stereotype.Component;

/**
 * 周转频次分项（算法 5.1 S_freq）：周转频次越高、库位离出库口越近，得分越高。
 * <p>公式：S_freq = turnoverNorm × proximity，其中
 * turnoverNorm = clamp(turnoverRate / maxTurnover)，proximity = (dMax - d) / (dMax - dMin)。</p>
 */
@Component
public class FreqScorer implements Scorer {

    @Override
    public String key() {
        return "freq";
    }

    @Override
    public double score(Sku sku, Location location, ScoringContext ctx) {
        double turnoverNorm = clamp(sku.getTurnoverRate() / ctx.getRules().getMaxTurnover());
        double proximity = ctx.proximity(location.getX(), location.getY());
        return turnoverNorm * proximity;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
