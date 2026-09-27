package com.wms.simulation.strategy;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.simulation.SimulationContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 分区存储策略（FR-3.1 第三种）：按品类分区，**区内就近**。
 *
 * <p>分区口径：库位分区由所属货架的 {@code racks.aisle} 经
 * {@code wms.zone.aisle-category} 映射派生（见 {@code ZoneProperties}），
 * 与 b 的评分 S_other 品类匹配用的是同一套口径——两处不会打架。
 *
 * <p>选位顺序：
 * <ol>
 *   <li>先取「分区品类 == 货物品类」的库位，在其内部选距出库口最近的一个；</li>
 *   <li>若无命中分区（货物品类为空、或该品类没有配置巷道、或分区已满），
 *       退化为全量就近，并在理由里明确写出「未命中分区」——避免给出误导性的理由。</li>
 * </ol>
 *
 * @author c
 */
@Component
public class ZoningStrategy implements InboundStrategy {

    @Override
    public String name() {
        return "zoning";
    }

    @Override
    public String displayName() {
        return "分区存储";
    }

    @Override
    public String description() {
        return "按品类分区，区内就近；无匹配分区时退化为就近分配";
    }

    @Override
    public StrategyPick pick(Sku sku, int quantity, List<Location> candidates, SimulationContext ctx) {
        StrategySupport.ensureCandidates(displayName(), candidates);

        String category = sku.getCategory();
        List<Location> inZone = candidates.stream()
                .filter(location -> category != null && category.equals(ctx.zoneOf(location)))
                .toList();

        boolean zoneHit = !inZone.isEmpty();
        List<Location> pool = zoneHit ? inZone : candidates;
        Location chosen = pool.stream()
                .min(Comparator.comparingDouble(ctx::distanceToExit).thenComparing(Location::getId))
                .orElseThrow();

        double distance = ctx.distanceToExit(chosen);
        String reason = zoneHit
                ? String.format("分区命中「%s」区，区内就近：%s（%.1f）", category, chosen.getCode(), distance)
                : String.format("未命中「%s」分区（该品类无可用分区库位），退化为就近：%s（%.1f）",
                        category == null ? "未分类" : category, chosen.getCode(), distance);
        return StrategySupport.pick(ctx, sku, chosen, reason, distance);
    }
}
