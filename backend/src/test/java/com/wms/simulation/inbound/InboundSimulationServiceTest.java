package com.wms.simulation.inbound;

import com.wms.common.BizException;
import com.wms.config.ZoneProperties;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Plan;
import com.wms.domain.entity.Rack;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import com.wms.domain.repository.WarehouseDataRepository;
import com.wms.recommend.config.service.ConfigService;
import com.wms.recommend.engine.FreqScorer;
import com.wms.recommend.engine.OtherScorer;
import com.wms.recommend.engine.PriorityScorer;
import com.wms.recommend.engine.ScoreWeightConfig;
import com.wms.recommend.engine.ScoringEngine;
import com.wms.recommend.engine.StorageRuleConfig;
import com.wms.recommend.engine.WeightScorer;
import com.wms.simulation.SimulationStore;
import com.wms.simulation.inbound.dto.InboundItemRequest;
import com.wms.simulation.inbound.dto.InboundResetResult;
import com.wms.simulation.inbound.dto.InboundSimulationDto;
import com.wms.simulation.inbound.dto.InboundSimulationRequest;
import com.wms.simulation.strategy.FifoStrategy;
import com.wms.simulation.strategy.GradingStrategy;
import com.wms.simulation.strategy.NearestStrategy;
import com.wms.simulation.strategy.RandomStrategy;
import com.wms.simulation.strategy.SmartStrategy;
import com.wms.simulation.strategy.StrategyRegistry;
import com.wms.simulation.strategy.ZoningStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 入库仿真引擎单元测试（T-2 / C-B3）。
 *
 * <p>用 Mockito 提供基础数据（不启 Spring 上下文，符合《代码规范》4.7），
 * 验证：逐项选位不重复占位、选位理由与告警、方案快照（含 locationMap 与布局口径统计）、
 * 以及「清空重置」会连同方案一起清除。
 *
 * @author c
 */
class InboundSimulationServiceTest {

    private WarehouseDataRepository repository;
    private ConfigService configService;
    private InboundSimulationService service;

    private Location electronicsZoneLow;
    private Location foodZoneHigh;
    private Sku lightSku;
    private Sku heavySku;

    @BeforeEach
    void setUp() {
        repository = mock(WarehouseDataRepository.class);
        configService = mock(ConfigService.class);

        StorageRuleConfig rules = new StorageRuleConfig();
        rules.setHeavyWeightThreshold(100);
        rules.setMaxLayerForHeavy(2);
        when(configService.getRules()).thenReturn(rules);
        when(configService.getWeights()).thenReturn(new ScoreWeightConfig());

        ZoneProperties zones = new ZoneProperties();
        zones.setAisleCategory(Map.of("A", "电子", "B", "食品"));

        ScoringEngine engine = new ScoringEngine(List.of(
                new WeightScorer(), new FreqScorer(), new PriorityScorer(), new OtherScorer()));
        StrategyRegistry registry = new StrategyRegistry(List.of(
                new RandomStrategy(), new NearestStrategy(), new ZoningStrategy(),
                new GradingStrategy(), new FifoStrategy(), new SmartStrategy(engine)));

        service = new InboundSimulationService(repository, registry, configService, zones,
                new SimulationStore());

        Warehouse warehouse = new Warehouse(1L, "一号仓", 0, 0);
        electronicsZoneLow = location(11L, "A-01-01-01", 2, 2, 1, 1L, "电子");
        foodZoneHigh = location(12L, "B-01-01-03", 10, 2, 3, 2L, "食品");
        lightSku = new Sku(1L, "SKU-001", "高频电子元件", 50, 0.9, 5, "电子");
        heavySku = new Sku(2L, "SKU-002", "重型机械部件", 180, 0.2, 3, "机械");

        when(repository.findWarehouse(1L)).thenReturn(Optional.of(warehouse));
        when(repository.listRacks(1L)).thenReturn(List.of(rack(1L, "A"), rack(2L, "B")));
        when(repository.listFreeLocations(1L)).thenReturn(List.of(electronicsZoneLow, foodZoneHigh));
        when(repository.occupiedSnapshot(anyLong())).thenReturn(Map.of());
        when(repository.findSku(1L)).thenReturn(Optional.of(lightSku));
        when(repository.findSku(2L)).thenReturn(Optional.of(heavySku));
        when(repository.savePlan(any(Plan.class))).thenAnswer(invocation -> {
            Plan plan = invocation.getArgument(0);
            plan.setId(99L);
            return plan;
        });
        when(repository.deletePlan(99L)).thenReturn(true);
    }

    private static Location location(long id, String code, int x, int y, int layer,
                                     long rackId, String category) {
        Location location = new Location(id, code, x, y, layer, 1L);
        location.setRackId(rackId);
        location.setCategory(category);
        location.setCapacity(BigDecimal.valueOf(1000));
        return location;
    }

    private static Rack rack(long id, String aisle) {
        Rack rack = new Rack();
        rack.setId(id);
        rack.setWarehouseId(1L);
        rack.setAisle(aisle);
        rack.setCode(aisle + "-01");
        return rack;
    }

    @Test
    @DisplayName("入库仿真：多条明细应分配到不同库位，且生成带 locationMap 的方案")
    void should_assign_distinct_locations_and_save_plan() {
        InboundSimulationDto dto = service.simulate(new InboundSimulationRequest(1L, "nearest",
                List.of(new InboundItemRequest(1L, 10), new InboundItemRequest(1L, 5)),
                null, null, null, null));

        assertEquals("INB-001", dto.simulationId());
        assertEquals(2, dto.assignmentCount());
        assertEquals(2, dto.assignments().stream().map(a -> a.locationId()).distinct().count(),
                "同一库位不能在一次仿真里被占用两次");
        assertNotNull(dto.planId());
        assertEquals("nearest", dto.strategy());
        assertEquals("就近分配", dto.strategyName());

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(repository).savePlan(captor.capture());
        Plan plan = captor.getValue();
        assertEquals(2, plan.getLocationMap().size());
        assertEquals(1L, plan.getLocationMap().get("11").skuId());
        assertEquals(10, plan.getLocationMap().get("11").quantity());
        assertEquals("INB-001", plan.getSimulationId());
    }

    @Test
    @DisplayName("入库仿真：布局口径统计 = 各库位到出库口距离的平均（路程不按件数重复计）")
    void should_compute_layout_metrics() {
        InboundSimulationDto dto = service.simulate(new InboundSimulationRequest(1L, "fifo",
                List.of(new InboundItemRequest(1L, 4)), null, null, null, null));

        // FIFO 取编码最小的 A-01-01-01，距出库口 |2-0|+|2-0| = 4
        assertEquals("A-01-01-01", dto.assignments().get(0).code());
        assertEquals(4.0, dto.totalDistance(), 1e-9);
        assertEquals(4.0, dto.avgDistance(), 1e-9);
        // SKU-001 周转频次 0.9 ≥ 0.5 属高频，其平均距离同样是 4
        assertEquals(4.0, dto.hotAvgDistance(), 1e-9);
        assertEquals(0, dto.violations());
    }

    @Test
    @DisplayName("入库仿真：就近分配把重货放到高层时计入 violations 并给出告警")
    void should_count_violations_for_soft_constraint() {
        when(repository.listFreeLocations(1L)).thenReturn(List.of(foodZoneHigh));

        InboundSimulationDto dto = service.simulate(new InboundSimulationRequest(1L, "nearest",
                List.of(new InboundItemRequest(2L, 1)), null, null, null, null));

        assertEquals(1, dto.violations());
        assertNotNull(dto.assignments().get(0).warning());
        assertTrue(dto.assignments().get(0).warning().contains("重货"));
    }

    @Test
    @DisplayName("入库仿真：空闲库位为空时抛 42203")
    void should_fail_when_no_free_location() {
        when(repository.listFreeLocations(1L)).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class, () -> service.simulate(
                new InboundSimulationRequest(1L, "random",
                        List.of(new InboundItemRequest(1L, 1)), null, null, null, null)));
        assertEquals(com.wms.common.ErrorCode.NO_FREE_LOCATION.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("入库仿真：仓库不存在抛 40402，策略非法抛 40001")
    void should_validate_request() {
        BizException noWarehouse = assertThrows(BizException.class, () -> service.simulate(
                new InboundSimulationRequest(404L, "random",
                        List.of(new InboundItemRequest(1L, 1)), null, null, null, null)));
        assertEquals(com.wms.common.ErrorCode.WAREHOUSE_NOT_FOUND.getCode(), noWarehouse.getCode());

        BizException badStrategy = assertThrows(BizException.class, () -> service.simulate(
                new InboundSimulationRequest(1L, "not-exist",
                        List.of(new InboundItemRequest(1L, 1)), null, null, null, null)));
        assertEquals(com.wms.common.ErrorCode.PARAM_INVALID.getCode(), badStrategy.getCode());
    }

    @Test
    @DisplayName("清空重置：应丢弃仿真记录并删除其产出的方案")
    void should_reset_and_remove_plan() {
        InboundSimulationDto dto = service.simulate(new InboundSimulationRequest(1L, "random",
                List.of(new InboundItemRequest(1L, 1)), null, null, null, 7L));

        InboundResetResult result = service.reset(dto.simulationId());

        assertTrue(result.reset());
        assertEquals(99L, result.removedPlanId());
        verify(repository).deletePlan(99L);
        assertThrows(BizException.class, () -> service.get(dto.simulationId()));
    }
}
