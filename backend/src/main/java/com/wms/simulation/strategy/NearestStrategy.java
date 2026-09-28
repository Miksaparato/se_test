package com.wms.simulation.strategy;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.simulation.SimulationContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 就近分配策略（FR-3.1 第二种）：选择离出库口最近（曼哈顿距离最小）的空闲库位。
 *
 * <p>距离口径由 c 统一提供（COM-6），这里只调用
 * {@link SimulationContext#distanceToExit(Location)}，不自行实现公式。
 *
 * @author c
 */
@Component
public class NearestStrategy implements InboundStrategy {

    @Override
    public String name() {
        return "nearest";
    }

    @Override
    public String displayName() {
        return "就近分配";
    }

    @Override
    public String description() {
        return "选择距出库口曼哈顿距离最近的空闲库位";
    }

    @Override
    public StrategyPick pick(Sku sku, int quantity, List<Location> candidates, SimulationContext ctx) {
        StrategySupport.ensureCandidates(displayName(), candidates);
        Location nearest = candidates.stream()
                .min(Comparator.comparingDouble(ctx::distanceToExit)
                        .thenComparing(Location::getId))
                .orElseThrow();
        double distance = ctx.distanceToExit(nearest);
        String reason = String.format("距出库口最近：%s（%s %.1f）",
                nearest.getCode(), ctx.getDistanceMetric().displayName(), distance);
        return StrategySupport.pick(ctx, sku, nearest, reason, distance);
    }
}
