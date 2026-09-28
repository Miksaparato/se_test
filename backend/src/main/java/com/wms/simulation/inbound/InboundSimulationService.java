package com.wms.simulation.inbound;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.config.ZoneProperties;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Plan;
import com.wms.domain.entity.Rack;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import com.wms.domain.entity.type.LocationOccupancy;
import com.wms.domain.entity.type.PlanResult;
import com.wms.domain.repository.WarehouseDataRepository;
import com.wms.recommend.config.service.ConfigService;
import com.wms.recommend.engine.ScoreWeightConfig;
import com.wms.recommend.engine.StorageRuleConfig;
import com.wms.simulation.ConstraintChecker;
import com.wms.simulation.SimulationContext;
import com.wms.simulation.SimulationStore;
import com.wms.simulation.distance.DistanceMetric;
import com.wms.simulation.inbound.dto.InboundAssignment;
import com.wms.simulation.inbound.dto.InboundItemRequest;
import com.wms.simulation.inbound.dto.InboundResetResult;
import com.wms.simulation.inbound.dto.InboundSimulationDto;
import com.wms.simulation.inbound.dto.InboundSimulationRequest;
import com.wms.simulation.strategy.InboundStrategy;
import com.wms.simulation.strategy.StrategyPick;
import com.wms.simulation.strategy.StrategyRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 入库策略仿真引擎（C-B3 / FR-3.2，对应 API-055 ~ API-057）。
 *
 * <p>执行流程：校验仓库与策略 → 取当前空闲库位 → 按 {@code items} 顺序逐项选位
 * （每次选位立即在 {@link SimulationContext} 中标记占用，保证后续选位不会重复占用同一库位）
 * → 形成库位分配方案并落 {@code plans} 表 → 返回带理由的选位明细。
 *
 * <p><b>仿真不改库</b>：整个试算过程不写 {@code locations} 表
 * （《数据库设计说明书》5.2「仿真需在不改库的前提下试算」），只把方案快照写入 {@code plans}。
 * 「清空重置」（API-057）因此只需丢弃内存记录与方案快照。
 *
 * <p>方案统计口径：入库阶段先写**布局口径**的
 * {@code totalDistance / avgDistance / hotAvgDistance / violations}
 * （Σ 库位→出库口距离，按数量加权），让方案一产出就有可对比的量化指标；
 * 之后对其跑出库仿真（API-058）时，会用**订单口径**的真实统计覆盖这几项并补上
 * {@code orderCount / estimatedTime}。
 *
 * @author c
 */
@Service
public class InboundSimulationService {

    private static final Logger log = LoggerFactory.getLogger(InboundSimulationService.class);

    /** 高频货判定阈值：周转频次 ≥ 0.5 视为高频（用于 hotAvgDistance）。 */
    public static final double HOT_TURNOVER_THRESHOLD = 0.5;

    /** 随机策略的默认种子：固定值让「随机分配」结果可复现，便于复盘与测试。 */
    private static final long DEFAULT_RANDOM_SEED = 20260910L;

    private final WarehouseDataRepository repository;
    private final StrategyRegistry strategyRegistry;
    private final ConfigService configService;
    private final ZoneProperties zoneProperties;
    private final SimulationStore store;

    /**
     * 构造入库仿真服务。
     *
     * @param repository       基础数据仓储
     * @param strategyRegistry 策略注册表（插件式，C-B1）
     * @param configService    b 的权重/规则配置服务（复用管理员配置）
     * @param zoneProperties   分区（品类 ↔ 巷道）映射
     * @param store            仿真会话存储
     */
    public InboundSimulationService(WarehouseDataRepository repository,
                                    StrategyRegistry strategyRegistry,
                                    ConfigService configService,
                                    ZoneProperties zoneProperties,
                                    SimulationStore store) {
        this.repository = repository;
        this.strategyRegistry = strategyRegistry;
        this.configService = configService;
        this.zoneProperties = zoneProperties;
        this.store = store;
    }

    /**
     * 执行入库仿真（API-055）。
     *
     * @param request 仿真请求
     * @return 仿真结果（含方案 id 与逐次选位理由）
     * @throws BizException 仓库/货物不存在（404）、策略非法（40001）、无可用库位（42220）、无满足约束库位（42218）
     */
    @Transactional(rollbackFor = Exception.class)
    public InboundSimulationDto simulate(InboundSimulationRequest request) {
        Warehouse warehouse = repository.findWarehouse(request.warehouseId())
                .orElseThrow(() -> new BizException(ErrorCode.WAREHOUSE_NOT_FOUND,
                        "仓库不存在: " + request.warehouseId()));
        InboundStrategy strategy = strategyRegistry.get(request.strategy());

        StorageRuleConfig rules = configService.getRules();
        ScoreWeightConfig weights = configService.getWeights();
        DistanceMetric metric = DistanceMetric.parse(request.distanceMetric());
        double layerHeight = request.layerHeight() == null ? 1.0 : request.layerHeight();
        long seed = request.randomSeed() == null ? DEFAULT_RANDOM_SEED : request.randomSeed();

        String simulationId = store.nextInboundId();
        List<Rack> racks = repository.listRacks(warehouse.getId());
        SimulationContext ctx = new SimulationContext(simulationId, warehouse, racks, rules, weights,
                zoneProperties, metric, layerHeight, seed,
                repository.occupiedSnapshot(warehouse.getId()));

        // 候选池：当前真实空闲的库位；仿真过程中的占用由 context 维护
        List<Location> freeLocations = new ArrayList<>(repository.listFreeLocations(warehouse.getId()));
        if (freeLocations.isEmpty()) {
            throw new BizException(ErrorCode.NO_FREE_LOCATION,
                    "仓库「" + warehouse.getName() + "」暂无空闲库位");
        }

        List<InboundAssignment> assignments = new ArrayList<>();
        Map<String, LocationOccupancy> locationMap = new LinkedHashMap<>();
        double totalDistance = 0;
        double hotDistance = 0;
        int hotCount = 0;
        int violations = 0;

        for (InboundItemRequest item : request.items()) {
            Sku sku = repository.findSku(item.skuId())
                    .orElseThrow(() -> new BizException(ErrorCode.SKU_NOT_FOUND,
                            "货物不存在: " + item.skuId()));

            // C-B7 硬约束：库位可用（未被占用/未被本次仿真选走）+ 容量放得下
            List<Location> candidates = ConstraintChecker.hardFilter(sku, freeLocations, ctx);
            if (candidates.isEmpty()) {
                throw new BizException(ErrorCode.SIM_NO_AVAILABLE_LOCATION,
                        String.format("第 %d 项（%s）已无可用库位：可能空闲库位耗尽或容量不足",
                                assignments.size() + 1, sku.getCode()));
            }

            StrategyPick pick = strategy.pick(sku, item.quantity(), candidates, ctx);
            ctx.markOccupied(pick.location());

            assignments.add(new InboundAssignment(sku.getId(), sku.getCode(), sku.getName(),
                    pick.location().getId(), pick.location().getCode(), item.quantity(),
                    pick.score(), pick.reason(), pick.warning()));
            locationMap.put(String.valueOf(pick.location().getId()),
                    new LocationOccupancy(sku.getId(), item.quantity()));

            if (pick.warning() != null) {
                violations++;
            }
            // 搬运路程口径：每次入库占用一个库位，计一次「库位 → 出库口」的单程距离
            // （路程是行程里程，不按件数重复计；口径由 COM-6 统一，出库仿真同口径）
            double distance = ctx.distanceToExit(pick.location());
            totalDistance += distance;
            if (isHotSku(sku)) {
                hotDistance += distance;
                hotCount++;
            }
        }

        Plan plan = buildPlan(request, warehouse, strategy, simulationId, locationMap,
                rules, weights, metric, layerHeight, seed,
                totalDistance, assignments.size(), hotDistance, hotCount, violations);

        double avgDistance = assignments.isEmpty() ? 0 : totalDistance / assignments.size();
        double hotAvgDistance = hotCount == 0 ? 0 : hotDistance / hotCount;

        InboundSimulationDto dto = new InboundSimulationDto(simulationId, plan.getId(), plan.getPlanNo(),
                strategy.name(), strategy.displayName(), warehouse.getId(), assignments.size(),
                violations, round(totalDistance), round(avgDistance), round(hotAvgDistance),
                List.copyOf(assignments));
        store.saveInbound(dto);

        log.info("入库仿真完成 {} 策略={} 仓库={} 条目={} 违规={} 方案={}({})",
                simulationId, strategy.name(), warehouse.getId(), assignments.size(), violations,
                plan.getId(), plan.getPlanNo());
        return dto;
    }

    /**
     * 查询入库仿真记录（API-056）。
     *
     * @param simulationId 仿真单号
     * @return 仿真结果
     */
    public InboundSimulationDto get(String simulationId) {
        return store.getInbound(simulationId);
    }

    /**
     * 清空重置（API-057）：丢弃该次仿真的内存明细，并删除其产出的方案快照，
     * 以便换一种策略重新仿真（FR-3.2）。基础数据（库位/货物/订单）不受影响。
     *
     * @param simulationId 仿真单号
     * @return 重置结果
     */
    @Transactional(rollbackFor = Exception.class)
    public InboundResetResult reset(String simulationId) {
        InboundSimulationDto removed = store.removeInbound(simulationId)
                .orElseThrow(() -> new BizException(ErrorCode.SIMULATION_NOT_FOUND,
                        "入库仿真记录不存在或已重置：" + simulationId));
        Long removedPlanId = null;
        if (removed.planId() != null && repository.deletePlan(removed.planId())) {
            removedPlanId = removed.planId();
        }
        log.info("入库仿真重置 {} 已清除方案 {}", simulationId, removedPlanId);
        return new InboundResetResult(simulationId, removedPlanId, true);
    }

    /**
     * 按当前权重/规则/距离口径组装方案实体（params 快照 + location_map + 布局口径 result）。
     */
    private Plan buildPlan(InboundSimulationRequest request, Warehouse warehouse,
                           InboundStrategy strategy, String simulationId,
                           Map<String, LocationOccupancy> locationMap,
                           StorageRuleConfig rules, ScoreWeightConfig weights,
                           DistanceMetric metric, double layerHeight, long seed,
                           double totalDistance, int assignmentCount,
                           double hotDistance, int hotCount, int violations) {
        Plan plan = new Plan();
        plan.setPlanNo(nextPlanNo());
        plan.setName(request.name() != null && !request.name().isBlank()
                ? request.name().trim()
                : strategy.displayName() + "-" + locationMap.size() + "项");
        plan.setWarehouseId(warehouse.getId());
        plan.setSimulationId(simulationId);
        plan.setStrategy(strategy.name());
        plan.setStrategyName(strategy.displayName());
        plan.setParams(paramsSnapshot(request, rules, weights, metric, layerHeight, seed));
        plan.setLocationMap(locationMap);
        plan.setResult(new PlanResult(
                round(totalDistance),
                round(assignmentCount == 0 ? 0 : totalDistance / assignmentCount),
                round(hotCount == 0 ? 0 : hotDistance / hotCount),
                violations, 0, 0.0));
        return repository.savePlan(plan);
    }

    /**
     * 生成不与库中已有数据冲突的方案编号，形如 {@code PLAN-20260927-004}。
     *
     * <p>序号取自「当前前缀下的最大编号 + 1」而不是进程内计数器：{@code plans.plan_no} 有唯一键
     * {@code uk_plans_plan_no}，计数器一重启就从 1 重来会直接撞库（表现为 50001）。
     *
     * @return 方案编号
     */
    private String nextPlanNo() {
        String prefix = store.currentPlanNoPrefix();
        int sequence = repository.findMaxPlanNo(prefix)
                .map(no -> no.substring(prefix.length()))
                .map(tail -> {
                    try {
                        return Integer.parseInt(tail) + 1;
                    } catch (NumberFormatException ignored) {
                        return 1;
                    }
                })
                .orElse(1);
        return prefix + String.format("%03d", sequence);
    }

    /** 策略参数快照：写入 plans.params，便于方案复盘与对比时说明「参数差异」。 */
    private Map<String, Object> paramsSnapshot(InboundSimulationRequest request, StorageRuleConfig rules,
                                               ScoreWeightConfig weights, DistanceMetric metric,
                                               double layerHeight, long seed) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("strategy", request.strategy());
        params.put("itemCount", request.items().size());
        params.put("distanceMetric", metric.name());
        params.put("layerHeight", layerHeight);
        params.put("randomSeed", seed);
        Map<String, Object> weightMap = new LinkedHashMap<>();
        weightMap.put("weight", weights.getWeight());
        weightMap.put("freq", weights.getFreq());
        weightMap.put("priority", weights.getPriority());
        weightMap.put("other", weights.getOther());
        params.put("weights", weightMap);
        Map<String, Object> ruleMap = new LinkedHashMap<>();
        ruleMap.put("heavyWeightThreshold", rules.getHeavyWeightThreshold());
        ruleMap.put("maxLayerForHeavy", rules.getMaxLayerForHeavy());
        ruleMap.put("goldenZoneRadius", rules.getGoldenZoneRadius());
        ruleMap.put("categoryMatchEnabled", rules.isCategoryMatchEnabled());
        params.put("rules", ruleMap);
        Map<String, Object> zoneMap = new HashMap<>(zoneProperties.getAisleCategory());
        params.put("zoneAisleCategory", zoneMap);
        return params;
    }

    /**
     * 数值保留 3 位小数：避免浮点尾差让前端展示与方案对比出现 {@code 95.09999999999998}。
     *
     * @param value 原始值
     * @return 保留 3 位小数的值
     */
    static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /**
     * 判断货物是否属于高频货（供出库仿真复用同一阈值）。
     *
     * @param sku 货物
     * @return 高频返回 true
     */
    public static boolean isHotSku(Sku sku) {
        return sku != null && sku.turnoverRateValue() >= HOT_TURNOVER_THRESHOLD;
    }
}
