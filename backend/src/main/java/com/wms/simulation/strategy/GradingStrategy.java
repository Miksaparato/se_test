package com.wms.simulation.strategy;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.simulation.SimulationContext;
import com.wms.simulation.distance.DistanceUtil;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 分级存储策略（FR-3.1 第四种）：按频次/重量分级——**高频/轻货进黄金区，重货进低层**。
 *
 * <p>与「智能推荐」的区别：本策略不使用管理员可配置的权重与「其他」分项，
 * 只用两条固定业务规则，因此结果稳定、易解释，适合作为规则型对照方案。
 *
 * <p>评分公式（各分项归一化到 [0,1]）：
 * <pre>
 * weightNorm   = clamp(weight / maxWeightNorm)
 * turnoverNorm = clamp(turnoverRate / maxTurnover)
 * layerScore   = (maxLayer - layer + 1) / maxLayer      // 层越低越大
 * proxScore    = (dMax - d) / (dMax - dMin)             // 距出库口越近越大
 * score        = weightNorm × layerScore + turnoverNorm × proxScore
 * </pre>
 *
 * @author c
 */
@Component
public class GradingStrategy implements InboundStrategy {

    @Override
    public String name() {
        return "grading";
    }

    @Override
    public String displayName() {
        return "分级存储";
    }

    @Override
    public String description() {
        return "重货进低层、高频货进黄金区（固定规则，不依赖可配置权重）";
    }

    @Override
    public StrategyPick pick(Sku sku, int quantity, List<Location> candidates, SimulationContext ctx) {
        StrategySupport.ensureCandidates(displayName(), candidates);

        int maxLayer = candidates.stream()
                .mapToInt(DistanceUtil::layerOf)
                .max()
                .orElse(1);
        double dMin = candidates.stream().mapToDouble(ctx::distanceToExit).min().orElse(0);
        double dMax = candidates.stream().mapToDouble(ctx::distanceToExit).max().orElse(0);

        double weightNorm = clamp(sku.weightValue() / ctx.getRules().getMaxWeightNorm());
        double turnoverNorm = clamp(sku.turnoverRateValue() / ctx.getRules().getMaxTurnover());

        Location best = null;
        double bestScore = -1;
        for (Location location : candidates) {
            double layerScore = maxLayer <= 1 ? 1.0
                    : (maxLayer - DistanceUtil.layerOf(location) + 1) / (double) maxLayer;
            double proximity = dMax <= dMin ? 1.0 : (dMax - ctx.distanceToExit(location)) / (dMax - dMin);
            double score = weightNorm * layerScore + turnoverNorm * proximity;
            if (score > bestScore
                    || (score == bestScore && best != null
                        && ctx.distanceToExit(location) < ctx.distanceToExit(best))) {
                bestScore = score;
                best = location;
            }
        }
        Location chosen = best == null ? candidates.get(0) : best;
        String reason = describe(weightNorm, turnoverNorm, chosen, ctx);
        return StrategySupport.pick(ctx, sku, chosen, reason, round(bestScore));
    }

    private String describe(double weightNorm, double turnoverNorm, Location location, SimulationContext ctx) {
        String layerText = weightNorm >= 0.5
                ? String.format("重货入低层（第 %d 层）", DistanceUtil.layerOf(location))
                : String.format("轻货可上层（第 %d 层）", DistanceUtil.layerOf(location));
        String freqText = turnoverNorm >= 0.5 ? "高频货优先黄金区" : "低频货不占黄金区";
        return String.format("分级存储：%s + %s，选中 %s（距出库口 %.1f）",
                layerText, freqText, location.getCode(), ctx.distanceToExit(location));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
