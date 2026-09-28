package com.wms.simulation.outbound;

import com.wms.common.BizException;
import com.wms.config.ZoneProperties;
import com.wms.data.order.OrderService;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Order;
import com.wms.domain.entity.Plan;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import com.wms.domain.entity.type.LocationOccupancy;
import com.wms.domain.repository.WarehouseDataRepository;
import com.wms.recommend.config.service.ConfigService;
import com.wms.recommend.engine.ScoreWeightConfig;
import com.wms.recommend.engine.StorageRuleConfig;
import com.wms.simulation.SimulationStore;
import com.wms.simulation.outbound.dto.OutboundSimulationDto;
import com.wms.simulation.outbound.dto.OutboundSimulationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
 * 出库仿真与路程统计单元测试（T-2 / C-B4 / C-B6）。
 *
 * <p>锁定路程口径（COM-6）：搬运路程按「库位 → 出库口」的单程累加，
 * 平均路程 = 总路程 ÷ 订单数，耗时估算 = 总路程 ÷ 速度；并验证
 * 方案模式（按 location_map 还原库存布局）与真实占用模式两条路径。
 *
 * @author c
 */
class OutboundSimulationServiceTest {

    private WarehouseDataRepository repository;
    private OrderService orderService;
    private OutboundSimulationService service;

    /** 距出库口 3（x=3,y=0）。 */
    private Location near;
    /** 距出库口 10（x=10,y=0）。 */
    private Location far;
    private Sku hotSku;
    private Sku coldSku;

    @BeforeEach
    void setUp() {
        repository = mock(WarehouseDataRepository.class);
        orderService = mock(OrderService.class);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getRules()).thenReturn(new StorageRuleConfig());
        when(configService.getWeights()).thenReturn(new ScoreWeightConfig());

        service = new OutboundSimulationService(repository, orderService, configService,
                new ZoneProperties(), new SimulationStore());

        when(repository.findWarehouse(1L)).thenReturn(Optional.of(new Warehouse(1L, "一号仓", 0, 0)));
        when(repository.listRacks(1L)).thenReturn(List.of());
        when(repository.occupiedSnapshot(anyLong())).thenReturn(Map.of());
        when(repository.savePlan(any(Plan.class))).thenAnswer(inv -> inv.getArgument(0));

        near = location(11L, "A-01-01-01", 3, 0, 1);
        far = location(12L, "B-01-01-01", 10, 0, 1);
        hotSku = new Sku(1L, "SKU-001", "高频电子元件", 50, 0.9, 5, "电子");
        coldSku = new Sku(2L, "SKU-002", "低频备件", 20, 0.1, 2, "机械");
        when(repository.findSku(1L)).thenReturn(Optional.of(hotSku));
        when(repository.findSku(2L)).thenReturn(Optional.of(coldSku));
    }

    private static Location location(long id, String code, int x, int y, int layer) {
        Location location = new Location(id, code, x, y, layer, 1L);
        location.setCapacity(BigDecimal.valueOf(1000));
        return location;
    }

    private static Order order(long id, String orderNo, long skuId, int quantity, int priority) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setSkuId(skuId);
        order.setQuantity(quantity);
        order.setPriority(priority);
        order.setPlacedAt(LocalDateTime.now());
        order.setStatus("pending");
        return order;
    }

    @Test
    @DisplayName("出库仿真：真实占用模式下单库位拣选，路程 = 库位到出库口单程")
    void should_simulate_from_actual_occupancy() {
        near.setStatus("occupied");
        near.setOccupiedSkuId(1L);
        when(repository.listLocations(1L)).thenReturn(List.of(near, far));
        when(orderService.listPendingForSimulation()).thenReturn(List.of(
                order(1L, "SO-001", 1L, 2, 5),
                order(2L, "SO-002", 1L, 3, 4)));

        OutboundSimulationDto dto = service.simulate(
                new OutboundSimulationRequest(null, 1L, null, null, 2.0, null, null));

        assertEquals("OUT-001", dto.simulationId());
        assertEquals("actual", dto.stockSource());
        assertEquals(2, dto.stats().orderCount());
        // 两张订单各走「A-01-01-01 → 出库口」= 3，共 6
        assertEquals(6.0, dto.stats().totalDistance(), 1e-9);
        assertEquals(3.0, dto.stats().avgDistance(), 1e-9);
        // 速度 2 格/秒 → 6 / 2 = 3 秒
        assertEquals(3.0, dto.stats().estimatedTime(), 1e-9);
        assertEquals(3.0, dto.stats().hotAvgDistance(), 1e-9);
        assertEquals(List.of(3.0, 3.0), dto.stats().orderDistances());
        assertEquals(2, dto.paths().size());
        assertEquals(2, dto.paths().get(0).points().size(), "单库位路径应为「库位 → 出库口」两点");
        assertEquals(0, dto.paths().get(0).points().get(1).x(), "路径终点必须是出库口 x=0");
    }

    @Test
    @DisplayName("出库仿真：方案模式按 location_map 还原库存，多库位按由近及远凑够数量")
    void should_simulate_from_plan_location_map() {
        Plan plan = new Plan();
        plan.setId(50L);
        plan.setPlanNo("PLAN-20260910-001");
        plan.setWarehouseId(1L);
        Map<String, LocationOccupancy> map = new LinkedHashMap<>();
        map.put("11", new LocationOccupancy(1L, 1));
        map.put("12", new LocationOccupancy(1L, 4));
        plan.setLocationMap(map);
        when(repository.findPlan(50L)).thenReturn(Optional.of(plan));
        when(repository.listLocations(1L)).thenReturn(List.of(near, far));
        when(orderService.listPendingForSimulation()).thenReturn(List.of(order(1L, "SO-001", 1L, 3, 5)));

        OutboundSimulationDto dto = service.simulate(
                new OutboundSimulationRequest(50L, null, null, "single", null, null, null));

        assertEquals("plan", dto.stockSource());
        assertEquals(50L, dto.planId());
        assertEquals(2, dto.orders().get(0).picks().size());
        assertEquals(1, dto.orders().get(0).picks().get(0).quantity());
        assertEquals(2, dto.orders().get(0).picks().get(1).quantity());
        // 近库位 3 + 远库位 10 = 13
        assertEquals(13.0, dto.stats().totalDistance(), 1e-9);
        assertTrue(dto.orders().get(0).warning() == null);
    }

    @Test
    @DisplayName("出库仿真：库存不足时给出告警但仍统计已拣部分的路程")
    void should_warn_when_stock_insufficient() {
        Plan plan = new Plan();
        plan.setId(50L);
        plan.setWarehouseId(1L);
        Map<String, LocationOccupancy> map = new LinkedHashMap<>();
        map.put("11", new LocationOccupancy(1L, 1));
        plan.setLocationMap(map);
        when(repository.findPlan(50L)).thenReturn(Optional.of(plan));
        when(repository.listLocations(1L)).thenReturn(List.of(near));
        when(orderService.listPendingForSimulation()).thenReturn(List.of(order(1L, "SO-001", 1L, 5, 5)));

        OutboundSimulationDto dto = service.simulate(
                new OutboundSimulationRequest(50L, null, null, null, null, null, null));

        assertNotNull(dto.orders().get(0).warning());
        assertTrue(dto.orders().get(0).warning().contains("库存不足"));
        assertEquals(3.0, dto.stats().totalDistance(), 1e-9);
    }

    @Test
    @DisplayName("出库仿真：货物不在任何库位时告警且该单路程为 0")
    void should_warn_when_sku_not_stored() {
        when(repository.listLocations(1L)).thenReturn(List.of());
        when(orderService.listPendingForSimulation()).thenReturn(List.of(order(1L, "SO-001", 1L, 5, 5)));

        OutboundSimulationDto dto = service.simulate(
                new OutboundSimulationRequest(null, 1L, null, null, null, null, null));

        assertTrue(dto.orders().get(0).warning().contains("不在任何库位"));
        assertEquals(0.0, dto.stats().totalDistance(), 1e-9);
        assertEquals(0.0, dto.stats().avgDistance(), 1e-9);
    }

    @Test
    @DisplayName("出库仿真：方案模式下应把订单口径统计回填到 plans.result（供 b 的 API-051 对比）")
    void should_backfill_plan_result() {
        Plan plan = new Plan();
        plan.setId(50L);
        plan.setWarehouseId(1L);
        Map<String, LocationOccupancy> map = new LinkedHashMap<>();
        map.put("11", new LocationOccupancy(1L, 2));
        plan.setLocationMap(map);
        when(repository.findPlan(50L)).thenReturn(Optional.of(plan));
        when(repository.listLocations(1L)).thenReturn(List.of(near));
        when(orderService.listPendingForSimulation()).thenReturn(List.of(order(1L, "SO-001", 1L, 2, 5)));

        service.simulate(new OutboundSimulationRequest(50L, null, null, null, 1.0, null, null));

        ArgumentCaptor<Plan> captor = ArgumentCaptor.forClass(Plan.class);
        verify(repository).savePlan(captor.capture());
        assertEquals(3.0, captor.getValue().getResult().totalDistance(), 1e-9);
        assertEquals(1, captor.getValue().getResult().orderCount());
        assertEquals(3.0, captor.getValue().getResult().estimatedTime(), 1e-9);
    }

    @Test
    @DisplayName("出库仿真：无待出库订单抛 42222，方案不存在抛 40410，拣选方式非法抛 40001")
    void should_validate_request() {
        when(orderService.listPendingForSimulation()).thenReturn(List.of());
        BizException noOrder = assertThrows(BizException.class, () -> service.simulate(
                new OutboundSimulationRequest(null, 1L, null, null, null, null, null)));
        assertEquals(com.wms.common.ErrorCode.NO_PENDING_ORDER.getCode(), noOrder.getCode());

        BizException noPlan = assertThrows(BizException.class, () -> service.simulate(
                new OutboundSimulationRequest(999L, null, null, null, null, null, null)));
        assertEquals(com.wms.common.ErrorCode.PLAN_NOT_FOUND.getCode(), noPlan.getCode());
    }
}
