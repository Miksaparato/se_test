package com.wms.recommend.engine;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 推荐性能测试（B-B8 / NFR-1）：万库位规模下推荐计算 <= 5s。
 */
class PerformanceTest {

    @Test
    void should_score_10000_locations_under_5_seconds() {
        Warehouse warehouse = new Warehouse(1L, "一号仓", 0, 0);
        Sku sku = new Sku(1L, "SKU-001", "高频电子元件", 50, 0.9, 5, "电子");

        List<Location> free = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            int x = i % 100;
            int y = i / 100;
            free.add(new Location((long) (10_000 + i), "L" + i, x, y, (i % 3) + 1, 1L));
        }

        ScoringEngine engine = new ScoringEngine(List.of(
                new WeightScorer(), new FreqScorer(), new PriorityScorer(), new OtherScorer()));

        long start = System.nanoTime();
        List<LocationScore> result = engine.score(sku, free, warehouse,
                new ScoreWeightConfig(), new StorageRuleConfig(), Map.of());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(result.size() > 0);
        assertTrue(elapsedMs < 5_000,
                "万库位推荐应在 5s 内完成，实际 " + elapsedMs + "ms");
    }
}
