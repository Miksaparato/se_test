package com.wms.recommend.engine;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;

/**
 * 库位分层规则配置（对应 API-046 / API-047 与 FR-2.3、算法 5.4）。
 * 例如「第 1 层放重货」即：重货（重量 >= heavyWeightThreshold）只允许存放在
 * 层号 <= maxLayerForHeavy 的库位。
 */
public class StorageRuleConfig {

    /** 重货判定阈值（kg）。 */
    private double heavyWeightThreshold = 100;
    /** 重货允许的最高层号（1 为最底层）。 */
    private int maxLayerForHeavy = 2;
    /** 重量归一化上限（kg），超出按 1 处理。 */
    private double maxWeightNorm = 200;
    /** 周转频次归一化上限，超出按 1 处理。 */
    private double maxTurnover = 1.0;
    /** 出库优先级归一化上限（通常为 5）。 */
    private int maxPriority = 5;
    /** 黄金区半径（曼哈顿距离），距离出库口在该值以内视为黄金区。 */
    private double goldenZoneRadius = 8.0;
    /** 是否启用品类匹配分项。 */
    private boolean categoryMatchEnabled = true;

    public StorageRuleConfig() {
    }

    /** 校验阈值合法性，非法抛 400。 */
    public void validate() {
        if (heavyWeightThreshold <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "重货阈值必须大于 0");
        }
        if (maxLayerForHeavy < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "重货允许最高层号必须 >= 1");
        }
        if (maxWeightNorm <= 0 || maxTurnover <= 0 || maxPriority <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "归一化上限必须大于 0");
        }
    }

    /** 硬约束：重货是否允许放入指定层（算法 5.4「重货禁止分配到超过预设层高」）。 */
    public boolean allowsHeavyOnLayer(double weight, int layer) {
        if (weight < heavyWeightThreshold) {
            return true;
        }
        return layer <= maxLayerForHeavy;
    }

    public double getHeavyWeightThreshold() {
        return heavyWeightThreshold;
    }

    public void setHeavyWeightThreshold(double heavyWeightThreshold) {
        this.heavyWeightThreshold = heavyWeightThreshold;
    }

    public int getMaxLayerForHeavy() {
        return maxLayerForHeavy;
    }

    public void setMaxLayerForHeavy(int maxLayerForHeavy) {
        this.maxLayerForHeavy = maxLayerForHeavy;
    }

    public double getMaxWeightNorm() {
        return maxWeightNorm;
    }

    public void setMaxWeightNorm(double maxWeightNorm) {
        this.maxWeightNorm = maxWeightNorm;
    }

    public double getMaxTurnover() {
        return maxTurnover;
    }

    public void setMaxTurnover(double maxTurnover) {
        this.maxTurnover = maxTurnover;
    }

    public int getMaxPriority() {
        return maxPriority;
    }

    public void setMaxPriority(int maxPriority) {
        this.maxPriority = maxPriority;
    }

    public double getGoldenZoneRadius() {
        return goldenZoneRadius;
    }

    public void setGoldenZoneRadius(double goldenZoneRadius) {
        this.goldenZoneRadius = goldenZoneRadius;
    }

    public boolean isCategoryMatchEnabled() {
        return categoryMatchEnabled;
    }

    public void setCategoryMatchEnabled(boolean categoryMatchEnabled) {
        this.categoryMatchEnabled = categoryMatchEnabled;
    }
}
