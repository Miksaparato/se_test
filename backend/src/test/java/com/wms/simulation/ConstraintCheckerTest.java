package com.wms.simulation;

import com.wms.common.BizException;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.type.SkuSize;
import com.wms.recommend.engine.StorageRuleConfig;
import com.wms.simulation.distance.DistanceMetric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 约束校验单元测试（T-2 / C-B7，对应《需求文档》4.4 三条约束）。
 *
 * <p>重点验证「重货层高是软约束」这一口径：它只判定与计数，不把候选直接排除，
 * 否则方案对比里的 violations 指标会恒为 0。
 *
 * @author c
 */
class ConstraintCheckerTest {

    private static Sku sku(double weight, int volume) {
        Sku sku = new Sku(1L, "SKU-001", "测试货物", weight, 0.5, 3, "电子");
        sku.setSize(volume <= 0 ? null : new SkuSize(volume, 1, 1));
        return sku;
    }

    private static Location location(long id, int layer, double capacity) {
        Location location = new Location(id, "A-01-01-0" + layer, 2, 2, layer, 1L);
        location.setCapacity(BigDecimal.valueOf(capacity));
        return location;
    }

    private static SimulationContext context(List<Location> locations) {
        StorageRuleConfig rules = new StorageRuleConfig();
        rules.setHeavyWeightThreshold(100);
        rules.setMaxLayerForHeavy(2);
        return new SimulationContext("INB-001", new com.wms.domain.entity.Warehouse(1L, "一号仓", 0, 0),
                List.of(), rules, new com.wms.recommend.engine.ScoreWeightConfig(),
                new com.wms.config.ZoneProperties(), DistanceMetric.MANHATTAN, 1.0, 1L, java.util.Map.of());
    }

    @Test
    @DisplayName("容量校验：体积超过库位容量应被判定为放不下")
    void should_reject_when_volume_exceeds_capacity() {
        assertFalse(ConstraintChecker.fitsCapacity(sku(10, 200), location(1L, 1, 100)));
        assertTrue(ConstraintChecker.fitsCapacity(sku(10, 100), location(2L, 1, 100)));
    }

    @Test
    @DisplayName("容量校验：未录入尺寸时视为无容量约束")
    void should_pass_when_size_absent() {
        assertTrue(ConstraintChecker.fitsCapacity(sku(10, 0), location(1L, 1, 1)));
    }

    @Test
    @DisplayName("重货层高：重货放在超过阈值的层上应判违规")
    void should_detect_heavy_layer_violation() {
        StorageRuleConfig rules = new StorageRuleConfig();
        rules.setHeavyWeightThreshold(100);
        rules.setMaxLayerForHeavy(2);

        assertTrue(ConstraintChecker.violatesHeavyLayer(rules, sku(150, 0), location(1L, 3, 100)));
        assertFalse(ConstraintChecker.violatesHeavyLayer(rules, sku(150, 0), location(2L, 2, 100)));
        assertFalse(ConstraintChecker.violatesHeavyLayer(rules, sku(50, 0), location(3L, 3, 100)));
    }

    @Test
    @DisplayName("硬约束过滤：容量不足与已被仿真占用的库位都会被剔除")
    void should_filter_by_hard_constraints() {
        Location small = location(1L, 1, 50);
        Location enough = location(2L, 1, 100);
        Location picked = location(3L, 1, 100);
        SimulationContext ctx = context(List.of(small, enough, picked));
        ctx.markOccupied(picked);

        List<Location> eligible = ConstraintChecker.hardFilter(sku(10, 80), List.of(small, enough, picked), ctx);

        assertEquals(List.of(enough), eligible);
    }

    @Test
    @DisplayName("硬约束过滤：候选全被剔除时返回空列表，由调用方抛 42220")
    void should_return_empty_when_all_filtered() {
        SimulationContext ctx = context(List.of());
        assertTrue(ConstraintChecker.hardFilter(sku(10, 500), List.of(location(1L, 1, 10)), ctx).isEmpty());
    }

    @Test
    @DisplayName("仿真上下文：占用快照应包含仿真过程中新选走的库位")
    void should_merge_simulated_occupancy_into_snapshot() {
        Location location = location(1L, 2, 100);
        SimulationContext ctx = context(List.of(location));

        assertTrue(ctx.occupiedSnapshot().isEmpty());
        ctx.markOccupied(location);

        assertTrue(ctx.occupiedSnapshot().containsKey("2,2,2"));
        assertFalse(ctx.isAvailable(location));
    }

    @Test
    @DisplayName("仿真上下文：距离统一走 COM-6 口径（出库口 (0,0)）")
    void should_compute_distance_to_exit() {
        SimulationContext ctx = context(List.of());
        // |3-0| + |2-0| = 5
        assertEquals(5.0, ctx.distanceToExit(new Location(1L, "A-01-01-01", 3, 2, 1, 1L)));
        assertThrows(BizException.class, () -> ctx.distanceBetween(null, null));
    }
}
