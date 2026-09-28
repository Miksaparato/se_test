package com.wms.simulation.strategy;

import com.wms.domain.entity.Location;

import java.util.List;

/**
 * 一次入库选位的结果。
 *
 * <p>{@code reason} 对应 FR-3.2「记录每次入库选择的库位与**理由**」，
 * 会作为 API-055 响应里 {@code assignments[].reason} 返回，前端仿真页与报告直接展示。
 *
 * @param location 选中的库位
 * @param reason   选择理由（中文，可直接展示）
 * @param score    策略给出的适配分（无评分语义的策略填 0，仅作为排序参考展示）
 * @param warning  软约束告警（如「重货放入第 3 层，违反层高规则」），无告警时为 null
 * @author c
 */
public record StrategyPick(Location location, String reason, double score, String warning) {

    /**
     * 构造无告警的选位结果。
     *
     * @param location 选中的库位
     * @param reason   选择理由
     * @param score    适配分
     * @return 选位结果
     */
    public static StrategyPick of(Location location, String reason, double score) {
        return new StrategyPick(location, reason, score, null);
    }

    /**
     * 获取候选库位列表（便捷方法，供测试与日志）。
     *
     * @param picks 选位结果列表
     * @return 库位列表
     */
    public static List<Location> locationsOf(List<StrategyPick> picks) {
        return picks.stream().map(StrategyPick::location).toList();
    }
}
