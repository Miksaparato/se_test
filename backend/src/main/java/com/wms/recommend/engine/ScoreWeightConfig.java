package com.wms.recommend.engine;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;

/**
 * 评分权重配置（对应 API-044 / API-045 与 FR-2.1）。
 * 默认权重：重量 0.30、周转频次 0.40、出库优先级 0.20、其他 0.10，之和为 1。
 */
public class ScoreWeightConfig {

    private double weight = 0.30;
    private double freq = 0.40;
    private double priority = 0.20;
    private double other = 0.10;

    public ScoreWeightConfig() {
    }

    public ScoreWeightConfig(double weight, double freq, double priority, double other) {
        this.weight = weight;
        this.freq = freq;
        this.priority = priority;
        this.other = other;
    }

    public double sum() {
        return weight + freq + priority + other;
    }

    /** 校验：权重非负且之和为 1，否则抛 400 业务异常。 */
    public void validate() {
        if (weight < 0 || freq < 0 || priority < 0 || other < 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "权重不能为负");
        }
        double s = sum();
        if (Math.abs(s - 1.0) > 1e-6) {
            throw new BizException(ErrorCode.WEIGHT_SUM_INVALID,
                    "权重之和必须为 1，当前为 " + Math.round(s * 1000.0) / 1000.0);
        }
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public double getFreq() {
        return freq;
    }

    public void setFreq(double freq) {
        this.freq = freq;
    }

    public double getPriority() {
        return priority;
    }

    public void setPriority(double priority) {
        this.priority = priority;
    }

    public double getOther() {
        return other;
    }

    public void setOther(double other) {
        this.other = other;
    }
}
