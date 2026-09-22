package com.wms.recommend.engine;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 推荐理由生成器：根据分项原始得分，拼出可读的中文推荐理由（FR-2.2「带评分与理由」）。
 */
public final class ReasonBuilder {

    private ReasonBuilder() {
    }

    public static String build(Sku sku, Location loc, ScoringContext ctx, Map<String, Double> raw) {
        List<String> terms = new ArrayList<>();
        double weightNorm = clamp(sku.getWeight() / ctx.getRules().getMaxWeightNorm());
        double turnoverNorm = clamp(sku.getTurnoverRate() / ctx.getRules().getMaxTurnover());
        double priorityNorm = clamp(sku.getPriority() / (double) ctx.getRules().getMaxPriority());
        double proximity = ctx.proximity(loc.getX(), loc.getY());

        if (weightNorm >= 0.5 && loc.getLayer() <= ctx.getRules().getMaxLayerForHeavy()) {
            terms.add("低层适配重货");
        } else if (weightNorm < 0.3 && ctx.layerSuitability(loc.getLayer()) < 0.5) {
            terms.add("高层存放轻货");
        }

        if (turnoverNorm >= 0.5 && proximity >= 0.5) {
            terms.add("靠近出库口利于高频");
        }

        if (priorityNorm >= 0.6 && proximity >= 0.5) {
            terms.add("高优先级靠近出库口");
        }

        if (loc.getCategory() != null && loc.getCategory().equals(sku.getCategory())) {
            terms.add("品类匹配");
        }

        if (ctx.continuity(loc) >= 0.5) {
            terms.add("空位连续");
        }

        if (terms.isEmpty()) {
            return "综合评分最优";
        }
        return String.join(" + ", terms);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
