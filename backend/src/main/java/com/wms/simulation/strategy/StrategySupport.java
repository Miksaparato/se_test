package com.wms.simulation.strategy;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.simulation.ConstraintChecker;
import com.wms.simulation.SimulationContext;

import java.util.List;

/**
 * 策略实现的公共小工具，避免 6 个策略里重复写同样的空集校验与告警文案。
 *
 * @author c
 */
final class StrategySupport {

    private StrategySupport() {
    }

    /**
     * 候选库位为空时抛出业务异常（42220），提示中带上策略名便于定位。
     *
     * @param strategyName 策略中文名
     * @param candidates   候选库位
     */
    static void ensureCandidates(String strategyName, List<Location> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new BizException(ErrorCode.SIM_NO_AVAILABLE_LOCATION,
                    "「" + strategyName + "」已无可用库位（空闲库位可能已被占满或容量不足）");
        }
    }

    /**
     * 生成重货层高告警文案（软约束，命中即为该方案的一处 violations）。
     *
     * @param ctx      仿真上下文
     * @param sku      货物
     * @param location 选中的库位
     * @return 告警文案；未违规时返回 null
     */
    static String heavyWarning(SimulationContext ctx, Sku sku, Location location) {
        if (ConstraintChecker.violatesHeavyLayer(ctx.getRules(), sku, location)) {
            return String.format("重货（%.0fkg）放入第 %d 层，超过层高阈值 %d 层",
                    sku.weightValue(), location.getLayer(), ctx.getRules().getMaxLayerForHeavy());
        }
        return null;
    }

    /**
     * 生成选位结果，自动附带重货层高告警。
     *
     * @param ctx      仿真上下文
     * @param sku      货物
     * @param location 选中的库位
     * @param reason   选择理由
     * @param score    适配分
     * @return 选位结果
     */
    static StrategyPick pick(SimulationContext ctx, Sku sku, Location location,
                             String reason, double score) {
        return new StrategyPick(location, reason, score, heavyWarning(ctx, sku, location));
    }
}
