package com.wms.simulation.strategy;

import com.wms.config.ZoneProperties;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import com.wms.recommend.engine.FreqScorer;
import com.wms.recommend.engine.OtherScorer;
import com.wms.recommend.engine.PriorityScorer;
import com.wms.recommend.engine.ScoreWeightConfig;
import com.wms.recommend.engine.ScoringEngine;
import com.wms.recommend.engine.StorageRuleConfig;
import com.wms.recommend.engine.WeightScorer;
import com.wms.simulation.SimulationContext;
import com.wms.simulation.distance.DistanceMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 入库策略单元测试（T-2 / C-B2）。
 *
 * <p>六种策略各自的选位语义与理由文案都在这里锁定：新增策略时按同样的模式补用例，
 * 保证「插件式新增策略」不会悄悄改变既有策略行为（NFR-3）。
 *
 * @author c
 */
class InboundStrategyTest {

    private Warehouse warehouse;
    private StorageRuleConfig rules;
    private SimulationContext ctx;
    private List<Location> candidates;

    /** 库位 A-01-01-01：距出库口 4（x=2,y=2），第 1 层，属「电子」区。 */
    private Location electronicLow;
    /** 库位 B-01-01-03：距出库口 12（x=10,y=2），第 3 层，属「食品」区。 */
    private Location foodHigh;
    /** 库位 A-01-02-02：距出库口 8（x=6,y=2），第 2 层，属「电子」区。 */
    private Location electronicMid;

    @BeforeEach
    void setUp() {
        warehouse = new Warehouse(1L, "一号仓", 0, 0);
        rules = new StorageRuleConfig();
        rules.setHeavyWeightThreshold(100);
        rules.setMaxLayerForHeavy(2);

        electronicLow = location(11L, "A-01-01-01", 2, 2, 1, 1L, "电子");
        foodHigh = location(12L, "B-01-01-03", 10, 2, 3, 2L, "食品");
        electronicMid = location(13L, "A-01-02-02", 6, 2, 2, 1L, "电子");
        candidates = List.of(electronicLow, foodHigh, electronicMid);

        ZoneProperties zones = new ZoneProperties();
        zones.setAisleCategory(Map.of("A", "电子", "B", "食品"));

        ctx = new SimulationContext("INB-001", warehouse, List.of(), rules,
                new ScoreWeightConfig(), zones, DistanceMetric.MANHATTAN, 1.0, 42L, Map.of());
    }

    private static Location location(long id, String code, int x, int y, int layer,
                                     long rackId, String category) {
        Location location = new Location(id, code, x, y, layer, 1L);
        location.setRackId(rackId);
        location.setCategory(category);
        location.setCapacity(BigDecimal.valueOf(1000));
        return location;
    }

    private static Sku sku(double weight, double turnover, int priority, String category) {
        return new Sku(1L, "SKU-001", "测试货物", weight, turnover, priority, category);
    }

    @Test
    @DisplayName("就近分配：应选中距出库口曼哈顿距离最小的库位")
    void nearest_should_pick_closest_location() {
        StrategyPick pick = new NearestStrategy().pick(sku(10, 0.5, 3, "电子"), 1, candidates, ctx);
        assertEquals(electronicLow.getId(), pick.location().getId());
        assertTrue(pick.reason().contains("距出库口最近"));
    }

    @Test
    @DisplayName("先入先出：应选中库位编码最小的空位")
    void fifo_should_pick_smallest_code() {
        StrategyPick pick = new FifoStrategy().pick(sku(10, 0.5, 3, "电子"), 1, candidates, ctx);
        assertEquals("A-01-01-01", pick.location().getCode());
        assertTrue(pick.reason().contains("编号顺序"));
    }

    @Test
    @DisplayName("分区存储：应优先落在品类匹配的分区，且区内就近")
    void zoning_should_hit_matching_zone() {
        StrategyPick pick = new ZoningStrategy().pick(sku(10, 0.5, 3, "食品"), 1, candidates, ctx);
        assertEquals(foodHigh.getId(), pick.location().getId());
        assertTrue(pick.reason().contains("分区命中"));
    }

    @Test
    @DisplayName("分区存储：无匹配分区时退化为就近，并在理由里说明")
    void zoning_should_fallback_to_nearest() {
        StrategyPick pick = new ZoningStrategy().pick(sku(10, 0.5, 3, "服装"), 1, candidates, ctx);
        assertEquals(electronicLow.getId(), pick.location().getId());
        assertTrue(pick.reason().contains("未命中"));
    }

    @Test
    @DisplayName("随机分配：同一随机种子应给出可复现的结果")
    void random_should_be_reproducible_with_seed() {
        ZoneProperties zones = new ZoneProperties();
        SimulationContext first = new SimulationContext("INB-001", warehouse, List.of(), rules,
                new ScoreWeightConfig(), zones, DistanceMetric.MANHATTAN, 1.0, 7L, Map.of());
        SimulationContext second = new SimulationContext("INB-002", warehouse, List.of(), rules,
                new ScoreWeightConfig(), zones, DistanceMetric.MANHATTAN, 1.0, 7L, Map.of());

        RandomStrategy strategy = new RandomStrategy();
        assertEquals(strategy.pick(sku(10, 0.5, 3, "电子"), 1, candidates, first).location().getId(),
                strategy.pick(sku(10, 0.5, 3, "电子"), 1, candidates, second).location().getId());
    }

    @Test
    @DisplayName("分级存储：重货应避开高层（不选第 3 层）")
    void grading_should_avoid_high_layer_for_heavy_sku() {
        StrategyPick pick = new GradingStrategy().pick(sku(200, 0.1, 3, "机械"), 1, candidates, ctx);
        assertTrue(pick.location().getLayer() <= 2, "重货不应落在第 3 层");
        assertNull(pick.warning(), "分级存储应主动避开重货层高违规");
    }

    @Test
    @DisplayName("智能推荐：复用 b 的评分引擎，理由来自评分模型")
    void smart_should_reuse_scoring_engine() {
        ScoringEngine engine = new ScoringEngine(List.of(
                new WeightScorer(), new FreqScorer(), new PriorityScorer(), new OtherScorer()));
        StrategyPick pick = new SmartStrategy(engine).pick(sku(50, 0.9, 5, "电子"), 1, candidates, ctx);

        assertNotNull(pick.location());
        assertTrue(pick.reason().contains("智能推荐评分"));
        assertTrue(pick.score() > 0);
    }

    @Test
    @DisplayName("软约束：就近分配把重货放到高层时应给出告警并计入违规")
    void nearest_should_warn_when_heavy_goes_high() {
        StrategyPick pick = new NearestStrategy().pick(sku(200, 0.1, 3, "机械"), 1,
                List.of(foodHigh), ctx);

        assertEquals(foodHigh.getId(), pick.location().getId());
        assertNotNull(pick.warning());
        assertTrue(pick.warning().contains("重货"));
    }

    @Test
    @DisplayName("策略注册表：应注册 6 种内置策略，未知策略抛 40001")
    void registry_should_expose_all_builtin_strategies() {
        ScoringEngine engine = new ScoringEngine(List.of(
                new WeightScorer(), new FreqScorer(), new PriorityScorer(), new OtherScorer()));
        StrategyRegistry registry = new StrategyRegistry(List.of(
                new RandomStrategy(), new NearestStrategy(), new ZoningStrategy(),
                new GradingStrategy(), new FifoStrategy(), new SmartStrategy(engine)));

        assertEquals(List.of("fifo", "grading", "nearest", "random", "smart", "zoning"),
                registry.list().stream().map(InboundStrategy::name).toList());
        assertTrue(registry.names().contains("smart"));

        com.wms.common.BizException ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.wms.common.BizException.class, () -> registry.get("unknown"));
        assertEquals(com.wms.common.ErrorCode.PARAM_INVALID.getCode(), ex.getCode());
    }
}
