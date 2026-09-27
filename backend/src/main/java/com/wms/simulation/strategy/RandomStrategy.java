package com.wms.simulation.strategy;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.simulation.SimulationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 随机分配策略（FR-3.1 第一种）：从当前空闲库位中随机挑一个。
 *
 * <p>随机源来自 {@link SimulationContext#getRandom()}，种子由仿真服务固定传入，
 * 因此**同一仓库 + 同一策略 + 同一种子**的结果可复现（便于单元测试与结果复盘）。
 *
 * @author c
 */
@Component
public class RandomStrategy implements InboundStrategy {

    @Override
    public String name() {
        return "random";
    }

    @Override
    public String displayName() {
        return "随机分配";
    }

    @Override
    public String description() {
        return "从空闲库位中随机选择，作为对照基线";
    }

    @Override
    public StrategyPick pick(Sku sku, int quantity, List<Location> candidates, SimulationContext ctx) {
        StrategySupport.ensureCandidates(displayName(), candidates);
        Location location = candidates.get(ctx.getRandom().nextInt(candidates.size()));
        String reason = String.format("随机命中库位 %s（候选 %d 个）", location.getCode(), candidates.size());
        return StrategySupport.pick(ctx, sku, location, reason, 0.0);
    }
}
