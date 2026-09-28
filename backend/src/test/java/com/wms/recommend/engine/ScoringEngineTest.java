package com.wms.recommend.engine;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推荐引擎单元测试（T-1）：评分模型、归一化、排序、硬约束过滤。
 * 纯 JUnit 5，不启动 Spring 上下文（NFR-4）。
 */
class ScoringEngineTest {

    private static final Warehouse WAREHOUSE = new Warehouse(1L, "一号仓", 0, 0);

    private final ScoringEngine engine = new ScoringEngine(List.of(
            new WeightScorer(), new FreqScorer(), new PriorityScorer(), new OtherScorer()));

    @Test
    void should_score_lower_layer_higher_for_weight() {
        Sku sku = sku(80, 0.5, 3, "机械"); // 低于重货阈值，不触发硬过滤
        List<Location> free = List.of(
                loc(1L, 2, 2, 1, null),
                loc(2L, 2, 2, 3, null));

        List<LocationScore> result = engine.score(sku, free, WAREHOUSE, new ScoreWeightConfig(), new StorageRuleConfig(), Map.of());

        LocationScore low = find(result, 1L);
        LocationScore high = find(result, 2L);
        assertTrue(low.getSubScores().get("weight") > high.getSubScores().get("weight"),
                "低层库位的重量分项应高于高层");
        assertTrue(low.getScore() > high.getScore(), "低层库位综合分应更高");
    }

    @Test
    void should_score_near_exit_higher_for_high_freq() {
        Sku sku = sku(10, 0.9, 3, "电子"); // 高频
        List<Location> free = List.of(
                loc(1L, 2, 0, 1, null),  // 距出库口 2
                loc(2L, 6, 0, 1, null)); // 距出库口 6

        List<LocationScore> result = engine.score(sku, free, WAREHOUSE, new ScoreWeightConfig(), new StorageRuleConfig(), Map.of());

        LocationScore near = find(result, 1L);
        LocationScore far = find(result, 2L);
        assertTrue(near.getSubScores().get("freq") > far.getSubScores().get("freq"),
                "近出库口库位的频次分项应更高");
        assertTrue(near.getScore() > far.getScore());
    }

    @Test
    void should_exclude_heavy_sku_from_high_layer() {
        Sku heavy = sku(150, 0.3, 3, "机械"); // 超过阈值 100
        List<Location> free = List.of(
                loc(1L, 2, 2, 1, null),
                loc(2L, 2, 2, 3, null)); // 层 3 > 重货允许最高层 2

        List<LocationScore> result = engine.score(heavy, free, WAREHOUSE, new ScoreWeightConfig(), new StorageRuleConfig(), Map.of());

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getLocationId(), "高层库位应被硬约束过滤");
    }

    @Test
    void should_normalize_subscores_and_sum_to_total() {
        Sku sku = sku(120, 0.8, 4, "电子");
        List<Location> free = List.of(
                loc(1L, 2, 2, 1, "电子"),
                loc(2L, 4, 4, 2, null),
                loc(3L, 8, 6, 3, "食品"));

        List<LocationScore> result = engine.score(sku, free, WAREHOUSE, new ScoreWeightConfig(), new StorageRuleConfig(), Map.of());

        for (LocationScore ls : result) {
            for (Double v : ls.getSubScores().values()) {
                assertTrue(v >= 0.0 && v <= 1.0, "分项得分应归一化到 [0,1]，实际 " + v);
            }
            double sum = ls.getSubScores().values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(ls.getScore(), sum, 0.01, "总分应等于分项加权和");
            assertTrue(ls.getScore() >= 0.0 && ls.getScore() <= 1.0);
            assertFalse(ls.getReason().isBlank(), "应有推荐理由");
        }
    }

    @Test
    void should_sort_descending_by_score() {
        Sku sku = sku(50, 0.6, 3, "服装");
        List<Location> free = List.of(
                loc(1L, 2, 2, 1, null),
                loc(2L, 4, 4, 2, null),
                loc(3L, 6, 6, 3, null),
                loc(4L, 8, 8, 1, null));

        List<LocationScore> result = engine.score(sku, free, WAREHOUSE, new ScoreWeightConfig(), new StorageRuleConfig(), Map.of());

        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i - 1).getScore() >= result.get(i).getScore(), "结果应降序");
        }
    }

    @Test
    void should_return_empty_when_all_filtered() {
        Sku heavy = sku(180, 0.3, 3, "机械");
        List<Location> free = List.of(
                loc(1L, 2, 2, 3, null),
                loc(2L, 4, 4, 3, null)); // 全在高层，被重货规则过滤

        List<LocationScore> result = engine.score(heavy, free, WAREHOUSE, new ScoreWeightConfig(), new StorageRuleConfig(), Map.of());

        assertTrue(result.isEmpty());
    }

    private static Sku sku(double weight, double turnover, int priority, String category) {
        return new Sku(null, "SKU-X", "测试货物", weight, turnover, priority, category);
    }

    private static Location loc(Long id, int x, int y, int layer, String category) {
        Location l = new Location(id, "A-00-00-0" + layer, x, y, layer, 1L);
        l.setCategory(category);
        return l;
    }

    private static LocationScore find(List<LocationScore> list, Long id) {
        return list.stream().filter(l -> l.getLocationId().equals(id)).findFirst().orElseThrow();
    }
}
