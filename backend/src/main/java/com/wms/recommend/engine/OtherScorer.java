package com.wms.recommend.engine;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import org.springframework.stereotype.Component;

/**
 * 其他分项（算法 5.1 S_other）：品类匹配 + 空位连续性。
 * <p>公式：S_other = 0.6 × categoryMatch + 0.4 × continuity。</p>
 * <ul>
 *   <li>categoryMatch：库位分区与货物品类一致得 1，无分区约束得 0.5（中性），不一致得 0。</li>
 *   <li>continuity：同层正交四邻中被占用的比例，奖励填满连续空位。</li>
 * </ul>
 */
@Component
public class OtherScorer implements Scorer {

    private static final double CATEGORY_WEIGHT = 0.6;
    private static final double CONTINUITY_WEIGHT = 0.4;

    @Override
    public String key() {
        return "other";
    }

    @Override
    public double score(Sku sku, Location location, ScoringContext ctx) {
        double categoryMatch = categoryMatch(sku, location, ctx);
        double continuity = ctx.continuity(location);
        return CATEGORY_WEIGHT * categoryMatch + CONTINUITY_WEIGHT * continuity;
    }

    private double categoryMatch(Sku sku, Location location, ScoringContext ctx) {
        if (!ctx.getRules().isCategoryMatchEnabled()) {
            return 0.5;
        }
        if (location.getCategory() == null || location.getCategory().isBlank()) {
            return 0.5;
        }
        return location.getCategory().equals(sku.getCategory()) ? 1.0 : 0.0;
    }
}
