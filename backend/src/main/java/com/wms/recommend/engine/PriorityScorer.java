package com.wms.recommend.engine;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import org.springframework.stereotype.Component;

/**
 * 出库优先级分项（算法 5.1 S_priority）：优先级越高、库位越靠近出库口，得分越高。
 * <p>公式：S_priority = priorityNorm × proximity，其中 priorityNorm = clamp(priority / maxPriority)。</p>
 */
@Component
public class PriorityScorer implements Scorer {

    @Override
    public String key() {
        return "priority";
    }

    @Override
    public double score(Sku sku, Location location, ScoringContext ctx) {
        double priorityNorm = clamp(sku.getPriority() / (double) ctx.getRules().getMaxPriority());
        double proximity = ctx.proximity(location.getX(), location.getY());
        return priorityNorm * proximity;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
