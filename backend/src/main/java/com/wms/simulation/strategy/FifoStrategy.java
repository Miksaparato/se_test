package com.wms.simulation.strategy;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.simulation.SimulationContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 先入先出策略（FR-3.1 第六种）：按库位编号顺序分配，取编号最小的空位。
 *
 * <p>编号即 {@code locations.code}（巷道-货架-列-层），因此结果稳定、可预期，
 * 常被用作「固定规则」对照方案。同编号时按库位 id 兜底，保证顺序唯一。
 *
 * @author c
 */
@Component
public class FifoStrategy implements InboundStrategy {

    @Override
    public String name() {
        return "fifo";
    }

    @Override
    public String displayName() {
        return "先入先出（FIFO 空位）";
    }

    @Override
    public String description() {
        return "按库位编号顺序取第一个空位，规则固定、结果稳定";
    }

    @Override
    public StrategyPick pick(Sku sku, int quantity, List<Location> candidates, SimulationContext ctx) {
        StrategySupport.ensureCandidates(displayName(), candidates);
        Location first = candidates.stream()
                .min(Comparator.comparing(Location::getCode, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Location::getId))
                .orElseThrow();
        String reason = String.format("按库位编号顺序取首个空位 %s", first.getCode());
        return StrategySupport.pick(ctx, sku, first, reason, 0.0);
    }
}
