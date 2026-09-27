package com.wms.simulation.outbound;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.config.ZoneProperties;
import com.wms.data.order.OrderService;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Order;
import com.wms.domain.entity.Plan;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import com.wms.domain.entity.type.LocationOccupancy;
import com.wms.domain.entity.type.PlanResult;
import com.wms.domain.repository.WarehouseDataRepository;
import com.wms.recommend.config.service.ConfigService;
import com.wms.recommend.engine.StorageRuleConfig;
import com.wms.simulation.ConstraintChecker;
import com.wms.simulation.SimulationContext;
import com.wms.simulation.SimulationStore;
import com.wms.simulation.distance.DistanceMetric;
import com.wms.simulation.distance.DistanceUtil;
import com.wms.simulation.distance.Point;
import com.wms.simulation.inbound.InboundSimulationService;
import com.wms.simulation.outbound.dto.OutboundOrderResult;
import com.wms.simulation.outbound.dto.OutboundPath;
import com.wms.simulation.outbound.dto.OutboundPickItem;
import com.wms.simulation.outbound.dto.OutboundSimulationDto;
import com.wms.simulation.outbound.dto.OutboundSimulationRequest;
import com.wms.simulation.outbound.dto.OutboundStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出库仿真引擎（C-B4 / C-B6，对应 API-058 ~ API-061）。
 *
 * <p>执行流程：取订单（按「优先级降序 + 下达时间升序」，FR-4.1）→ 为每张订单检索其 SKU 所在库位
 * → 按距出库口由近及远取货凑够数量 → 记录单订单路程与拣选路径 → 汇总统计（FR-4.2 / FR-4.3）。
 *
 * <p><b>库存来源两种模式</b>：
 * <ul>
 *   <li>给定 {@code planId}：按该方案的 {@code location_map} 还原库存布局做拣选
 *       （用于「同一批货物、不同入库策略」的方案对比，FR-5.1）；</li>
 *   <li>不给 {@code planId}：按仓库当前真实占用（{@code locations.occupied_sku_id}）拣选。</li>
 * </ul>
 *
 * <p><b>路程口径（COM-6，冻结）</b>：搬运路程按「货物从库位搬到出库口」的单程计算
 * （《需求文档》1.4 术语表），一张订单涉及多个库位时累加各库位的单程；
 * 耗时估算 = 总路程 ÷ 平均移动速度。
 *
 * @author c
 */
@Service
public class OutboundSimulationService {

    private static final Logger log = LoggerFactory.getLogger(OutboundSimulationService.class);

    /** 拣选方式：按单拣选（本期唯一实现）。 */
    public static final String PICKING_MODE_SINGLE = "single";

    /** 默认平均移动速度（格/秒），用于耗时估算。 */
    public static final double DEFAULT_SPEED = 1.0;

    /** 库存来源：按方案占用映射拣选。 */
    public static final String STOCK_SOURCE_PLAN = "plan";

    /** 库存来源：按仓库当前真实占用拣选。 */
    public static final String STOCK_SOURCE_ACTUAL = "actual";

    private final WarehouseDataRepository repository;
    private final OrderService orderService;
    private final ConfigService configService;
    private final ZoneProperties zoneProperties;
    private final SimulationStore store;

    /**
     * 构造出库仿真服务。
     *
     * @param repository     基础数据仓储
     * @param orderService   a 的订单服务（跨模块只通过公开 Service 调用）
     * @param configService  b 的分层规则配置（用于统计重货层位违规数）
     * @param zoneProperties 分区（品类 ↔ 巷道）映射
     * @param store          仿真会话存储
     */
    public OutboundSimulationService(WarehouseDataRepository repository,
                                     OrderService orderService,
                                     ConfigService configService,
                                     ZoneProperties zoneProperties,
                                     SimulationStore store) {
        this.repository = repository;
        this.orderService = orderService;
        this.configService = configService;
        this.zoneProperties = zoneProperties;
        this.store = store;
    }

    /**
     * 执行出库仿真（API-058）。
     *
     * @param request 仿真请求
     * @return 仿真结果（统计 + 路径 + 逐单明细）
     * @throws BizException 方案/仓库不存在（404）、无可出库订单（42222）
     */
    @Transactional(rollbackFor = Exception.class)
    public OutboundSimulationDto simulate(OutboundSimulationRequest request) {
        Plan plan = null;
        Long warehouseId = request.warehouseId();
        if (request.planId() != null) {
            plan = repository.findPlan(request.planId())
                    .orElseThrow(() -> new BizException(ErrorCode.PLAN_NOT_FOUND,
                            "分配方案不存在: " + request.planId()));
            warehouseId = plan.getWarehouseId();
        }
        if (warehouseId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "planId 与 warehouseId 至少提供一个：给 planId 按方案占用拣选，给 warehouseId 按当前真实占用拣选");
        }
        final Long resolvedWarehouseId = warehouseId;
        Warehouse warehouse = repository.findWarehouse(resolvedWarehouseId)
                .orElseThrow(() -> new BizException(ErrorCode.WAREHOUSE_NOT_FOUND,
                        "仓库不存在: " + resolvedWarehouseId));

        List<Order> orders = resolveOrders(request.orderIds());
        if (orders.isEmpty()) {
            throw new BizException(ErrorCode.NO_PENDING_ORDER,
                    "没有可参与出库仿真的订单（orderIds 为空时只取 pending 状态订单）");
        }

        String pickingMode = request.pickingMode() == null || request.pickingMode().isBlank()
                ? PICKING_MODE_SINGLE : request.pickingMode().trim();
        if (!PICKING_MODE_SINGLE.equals(pickingMode)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "本期仅支持按单拣选（pickingMode=single），收到：" + pickingMode);
        }
        double speed = request.speed() == null || request.speed() <= 0 ? DEFAULT_SPEED : request.speed();
        DistanceMetric metric = DistanceMetric.parse(request.distanceMetric());
        double layerHeight = request.layerHeight() == null ? 1.0 : request.layerHeight();

        String simulationId = store.nextOutboundId();
        SimulationContext ctx = new SimulationContext(simulationId, warehouse,
                repository.listRacks(warehouseId), configService.getRules(), configService.getWeights(),
                zoneProperties, metric, layerHeight, 0L, Map.of());

        StockIndex stock = plan == null
                ? StockIndex.fromActualOccupancy(repository, warehouseId, ctx)
                : StockIndex.fromPlan(plan, repository, warehouseId, ctx);

        Map<Long, Sku> skuCache = new HashMap<>();
        List<OutboundOrderResult> orderResults = new ArrayList<>(orders.size());
        List<OutboundPath> paths = new ArrayList<>(orders.size());
        List<Double> orderDistances = new ArrayList<>(orders.size());
        double totalDistance = 0;
        double hotDistance = 0;
        int hotPickCount = 0;

        for (Order order : orders) {
            Sku sku = skuCache.computeIfAbsent(order.getSkuId(), id -> repository.findSku(id).orElse(null));
            String skuCode = sku == null ? null : sku.getCode();
            List<Location> holders = stock.holdersOf(order.getSkuId());

            List<OutboundPickItem> picks = new ArrayList<>();
            List<Point> points = new ArrayList<>();
            Point exit = ctx.getExitPoint();
            String warning = null;
            int remaining = order.getQuantity() == null ? 0 : order.getQuantity();

            if (holders.isEmpty()) {
                warning = "货物当前不在任何库位，无法出库（该订单不产生搬运路程）";
            } else {
                int available = holders.stream().mapToInt(stock::quantityOf).sum();
                if (available < remaining) {
                    warning = String.format("库存不足：需要 %d，库位现有 %d", remaining, available);
                }
                for (Location location : holders) {
                    if (remaining <= 0) {
                        break;
                    }
                    int take = Math.min(remaining, stock.quantityOf(location));
                    if (take <= 0) {
                        continue;
                    }
                    double legDistance = ctx.distanceToExit(location);
                    picks.add(new OutboundPickItem(location.getId(), location.getCode(), take, round(legDistance)));
                    points.add(DistanceUtil.pointOf(location));
                    points.add(exit);
                    remaining -= take;

                    // 搬运路程口径（COM-6）：每访问一个库位计一次「库位 → 出库口」单程，
                    // 不按件数重复计——路程统计的是行程里程（《需求文档》1.4 术语表）
                    totalDistance += legDistance;
                    if (InboundSimulationService.isHotSku(sku)) {
                        hotDistance += legDistance;
                        hotPickCount++;
                    }
                }
            }

            double orderDistance = picks.stream().mapToDouble(OutboundPickItem::distance).sum();
            orderDistances.add(round(orderDistance));
            orderResults.add(new OutboundOrderResult(order.getId(), order.getOrderNo(), order.getSkuId(),
                    skuCode, order.getQuantity(), order.getPriority(), order.getPlacedAt(),
                    List.copyOf(picks), round(orderDistance), warning));
            paths.add(new OutboundPath(order.getId(), order.getOrderNo(), List.copyOf(points)));
        }

        int orderCount = orderResults.size();
        OutboundStats stats = new OutboundStats(
                round(totalDistance), List.copyOf(orderDistances),
                round(orderCount == 0 ? 0 : totalDistance / orderCount),
                round(totalDistance / speed), orderCount,
                round(hotPickCount == 0 ? 0 : hotDistance / hotPickCount),
                speed, metric.name());

        Long planId = plan == null ? null : plan.getId();
        String planNo = plan == null ? null : plan.getPlanNo();

        // 方案口径回填：让 b 的方案对比（API-051）拿到订单口径的真实统计
        if (plan != null) {
            backfillPlanResult(plan, stats, stock, repository, configService.getRules());
        }

        OutboundSimulationDto dto = new OutboundSimulationDto(simulationId, planId, planNo,
                warehouseId, pickingMode, plan == null ? STOCK_SOURCE_ACTUAL : STOCK_SOURCE_PLAN,
                stats, List.copyOf(paths), List.copyOf(orderResults));
        store.saveOutbound(dto);

        log.info("出库仿真完成 {} 方案={} 订单={} 总路程={} 平均={} 耗时估算={}s",
                dto.simulationId(), planId, orderCount, stats.totalDistance(),
                stats.avgDistance(), stats.estimatedTime());
        return dto;
    }

    /**
     * 查询出库仿真记录（API-059）。
     *
     * @param simulationId 仿真单号
     * @return 仿真结果
     */
    public OutboundSimulationDto get(String simulationId) {
        return store.getOutbound(simulationId);
    }

    /**
     * 查询路程统计（API-060）。
     *
     * @param simulationId 仿真单号
     * @return 路程统计
     */
    public OutboundStats stats(String simulationId) {
        return store.getOutbound(simulationId).stats();
    }

    /**
     * 查询拣选路径坐标（API-061，供平面图绘制路径，FR-4.3）。
     *
     * @param simulationId 仿真单号
     * @return 路径列表
     */
    public List<OutboundPath> paths(String simulationId) {
        return store.getOutbound(simulationId).paths();
    }

    /**
     * 解析参与仿真的订单：显式给 orderIds 就按 id 取（保持传入顺序），否则取全部待出库订单。
     *
     * @param orderIds 订单 id 列表，可为空
     * @return 订单列表
     */
    private List<Order> resolveOrders(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return orderService.listPendingForSimulation();
        }
        Map<Long, Order> byId = new LinkedHashMap<>();
        orderService.listByIds(orderIds).forEach(order -> byId.put(order.getId(), order));
        // 保持调用方传入顺序，便于前端对照
        List<Order> ordered = new ArrayList<>(orderIds.size());
        for (Long id : orderIds) {
            Order order = byId.get(id);
            if (order == null) {
                throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单不存在: " + id);
            }
            ordered.add(order);
        }
        return ordered;
    }

    /**
     * 用订单口径的真实统计回填方案（c 写、b 读，见《数据库设计说明书》2.6）。
     *
     * <p>{@code violations} 由方案的 {@code location_map} 现场重算（重货层高软约束），
     * 与入库阶段写入的值一致；{@code orderCount / estimatedTime} 补上出库口径。
     *
     * @param plan           方案
     * @param stats          出库统计
     * @param stock          库存索引（含库位实体）
     * @param repository     仓储
     * @param rules          分层规则
     */
    private void backfillPlanResult(Plan plan, OutboundStats stats, StockIndex stock,
                                    WarehouseDataRepository repository, StorageRuleConfig rules) {
        int violations = 0;
        for (Location location : stock.allLocations()) {
            Long skuId = stock.skuIdOf(location);
            if (skuId == null) {
                continue;
            }
            Sku sku = repository.findSku(skuId).orElse(null);
            if (sku != null && ConstraintChecker.violatesHeavyLayer(rules, sku, location)) {
                violations++;
            }
        }
        plan.setResult(new PlanResult(stats.totalDistance(), stats.avgDistance(),
                stats.hotAvgDistance(), violations, stats.orderCount(), stats.estimatedTime()));
        repository.savePlan(plan);
    }

    /**
     * 库存索引：把「SKU → 可拣选库位（按距出库口由近及远）」建一次索引，
     * 避免逐单逐库位扫描（NFR-1）。
     */
    private static final class StockIndex {

        private final Map<Long, List<Location>> holdersBySku;
        private final Map<Long, Integer> quantityByLocationId;
        private final Map<Long, Long> skuByLocationId;
        private final List<Location> allLocations;

        private StockIndex(Map<Long, List<Location>> holdersBySku,
                           Map<Long, Integer> quantityByLocationId,
                           Map<Long, Long> skuByLocationId,
                           List<Location> allLocations) {
            this.holdersBySku = holdersBySku;
            this.quantityByLocationId = quantityByLocationId;
            this.skuByLocationId = skuByLocationId;
            this.allLocations = allLocations;
        }

        /** 按方案的 location_map 建索引（键为库位 id 字符串）。 */
        static StockIndex fromPlan(Plan plan, WarehouseDataRepository repository,
                                   Long warehouseId, SimulationContext ctx) {
            Map<Long, Location> byId = new HashMap<>();
            for (Location location : repository.listLocations(warehouseId)) {
                byId.put(location.getId(), location);
            }
            Map<Long, List<Location>> holders = new HashMap<>();
            Map<Long, Integer> quantities = new HashMap<>();
            Map<Long, Long> skuByLocation = new HashMap<>();
            List<Location> used = new ArrayList<>();
            for (Map.Entry<String, LocationOccupancy> entry : plan.getLocationMap().entrySet()) {
                Long locationId;
                try {
                    locationId = Long.valueOf(entry.getKey());
                } catch (NumberFormatException ignored) {
                    continue;
                }
                Location location = byId.get(locationId);
                LocationOccupancy occupancy = entry.getValue();
                if (location == null || occupancy == null || occupancy.skuId() == null) {
                    continue;
                }
                holders.computeIfAbsent(occupancy.skuId(), key -> new ArrayList<>()).add(location);
                quantities.merge(locationId, occupancy.quantity() == null ? 0 : occupancy.quantity(), Integer::sum);
                skuByLocation.put(locationId, occupancy.skuId());
                used.add(location);
            }
            holders.values().forEach(list -> list.sort(Comparator.comparingDouble(ctx::distanceToExit)
                    .thenComparing(Location::getId)));
            return new StockIndex(holders, quantities, skuByLocation, used);
        }

        /** 按仓库当前真实占用建索引（{@code status = occupied} 且 {@code occupied_sku_id} 非空）。 */
        static StockIndex fromActualOccupancy(WarehouseDataRepository repository,
                                              Long warehouseId, SimulationContext ctx) {
            Map<Long, List<Location>> holders = new HashMap<>();
            Map<Long, Integer> quantities = new HashMap<>();
            Map<Long, Long> skuByLocation = new HashMap<>();
            List<Location> used = new ArrayList<>();
            for (Location location : repository.listLocations(warehouseId)) {
                Long skuId = location.getOccupiedSkuId();
                if (!"occupied".equals(location.getStatus()) || skuId == null) {
                    continue;
                }
                holders.computeIfAbsent(skuId, key -> new ArrayList<>()).add(location);
                // 真实占用场景没有「该库位存了多少」的记录，按订单数量在拣选时扣减，
                // 这里用一个足够大的值表示「库位有货、可按需取」
                quantities.put(location.getId(), Integer.MAX_VALUE);
                skuByLocation.put(location.getId(), skuId);
                used.add(location);
            }
            holders.values().forEach(list -> list.sort(Comparator.comparingDouble(ctx::distanceToExit)
                    .thenComparing(Location::getId)));
            return new StockIndex(holders, quantities, skuByLocation, used);
        }

        List<Location> holdersOf(Long skuId) {
            return holdersBySku.getOrDefault(skuId, List.of());
        }

        int quantityOf(Location location) {
            return quantityByLocationId.getOrDefault(location.getId(), 0);
        }

        Long skuIdOf(Location location) {
            return skuByLocationId.get(location.getId());
        }

        List<Location> allLocations() {
            return allLocations;
        }
    }

    /**
     * 数值保留 3 位小数，避免浮点尾差影响展示与对比。
     *
     * @param value 原始值
     * @return 保留 3 位小数的值
     */
    static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
