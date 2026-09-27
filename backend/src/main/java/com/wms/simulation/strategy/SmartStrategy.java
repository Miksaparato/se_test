package com.wms.simulation.strategy;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.recommend.engine.LocationScore;
import com.wms.recommend.engine.ScoringEngine;
import com.wms.simulation.SimulationContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能推荐策略（FR-3.1 第五种）：**复用 b 的评分模型**（跨模块协作点，COM 契约）。
 *
 * <p>《代码规范》4.5 明确要求：「{@code smart} 策略通过 b 的 {@code ScoringEngine} 复用评分，
 * 禁止复制评分逻辑」。因此本类只做三件事：取管理员配置的权重/规则 → 调用
 * {@link ScoringEngine#score} → 取第一名，理由直接使用 b 生成的推荐理由（FR-2.2 的可解释性）。
 *
 * <p>评分引擎内部会按「重货层高」硬约束过滤候选，所以智能推荐方案的重货违规数恒为 0。
 *
 * @author c
 */
@Component
public class SmartStrategy implements InboundStrategy {

    private final ScoringEngine scoringEngine;

    /**
     * 构造策略。
     *
     * @param scoringEngine b 提供的评分引擎（B-B1）
     */
    public SmartStrategy(ScoringEngine scoringEngine) {
        this.scoringEngine = scoringEngine;
    }

    @Override
    public String name() {
        return "smart";
    }

    @Override
    public String displayName() {
        return "智能推荐";
    }

    @Override
    public String description() {
        return "复用评分模型（重量/频次/优先级/其他加权）取最高分库位";
    }

    @Override
    public StrategyPick pick(Sku sku, int quantity, List<Location> candidates, SimulationContext ctx) {
        StrategySupport.ensureCandidates(displayName(), candidates);

        List<LocationScore> scores = scoringEngine.score(sku, candidates, ctx.getWarehouse(),
                ctx.getWeights(), ctx.getRules(), ctx.occupiedSnapshot());
        if (scores.isEmpty()) {
            throw new BizException(ErrorCode.NO_ELIGIBLE_LOCATION,
                    "「" + displayName() + "」无满足约束的库位（重货层高规则可能过滤了全部空位）");
        }

        LocationScore top = scores.get(0);
        Location chosen = candidates.stream()
                .filter(location -> location.getId().equals(top.getLocationId()))
                .findFirst()
                .orElse(candidates.get(0));
        String reason = String.format("智能推荐评分 %.3f 最高：%s", top.getScore(), top.getReason());
        return StrategySupport.pick(ctx, sku, chosen, reason, top.getScore());
    }
}
