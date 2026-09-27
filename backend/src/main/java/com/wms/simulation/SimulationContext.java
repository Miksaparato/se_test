package com.wms.simulation;

import com.wms.config.ZoneProperties;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Rack;
import com.wms.domain.entity.Warehouse;
import com.wms.recommend.engine.ScoreWeightConfig;
import com.wms.recommend.engine.StorageRuleConfig;
import com.wms.simulation.distance.DistanceMetric;
import com.wms.simulation.distance.DistanceUtil;
import com.wms.simulation.distance.Point;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 仿真上下文：一次仿真过程中共享的只读配置 + 可变占用状态。
 *
 * <p><b>为什么要有它</b>：入库仿真必须**在不改库的前提下试算**
 * （《数据库设计说明书》5.2：「仿真需在不改库的前提下试算」），因此仿真期间的库位占用
 * 状态保存在本对象里，而不是写回 {@code locations} 表；只有「采用推荐」（API-043）这类
 * 明确动作才会真正落库。
 *
 * <p>上下文还承担两项职责：
 * <ul>
 *   <li>统一距离口径（COM-6）：所有策略取距离都走 {@link #distanceToExit(Location)}；</li>
 *   <li>统一分区口径：库位分区由 {@code racks.aisle} 经 {@link ZoneProperties} 派生，
 *       与 b 的评分 S_other 用的是同一套映射。</li>
 * </ul>
 *
 * @author c
 */
public class SimulationContext {

    private final Warehouse warehouse;
    private final StorageRuleConfig rules;
    private final ScoreWeightConfig weights;
    private final ZoneProperties zoneProperties;
    private final DistanceMetric distanceMetric;
    private final double layerHeight;

    /** 货架 id → 巷道，用于在实体未带分区时兜底派生。 */
    private final Map<Long, String> aisleByRack;

    /** 仿真期间被占用的库位 id（含仿真开始前就已被真实占用的库位）。 */
    private final Set<Long> occupiedLocationIds = new HashSet<>();

    /** 仿真开始前仓库的真实占用快照（key = "x,y,layer"），来自基础数据。 */
    private final Map<String, Boolean> baseOccupiedSnapshot;

    /** 仿真过程中新增的占用坐标，与 {@link #baseOccupiedSnapshot} 合并后交给评分模型算空位连续性。 */
    private final Map<String, Boolean> simulatedOccupied = new HashMap<>();

    /** 随机策略使用的可复现随机源。 */
    private final Random random;

    /** 仿真会话序号，用于生成可读的仿真单号（INB-001 / OUT-001）。 */
    private final String simulationId;

    /**
     * 构造仿真上下文。
     *
     * @param simulationId         仿真单号，如 INB-001
     * @param warehouse            仓库（含出库口坐标）
     * @param racks                仓库下的全部货架（用于巷道 → 分区兜底派生）
     * @param rules                分层规则（重货层高阈值等，来自 b 的 API-046 配置）
     * @param weights              评分权重（smart 策略复用 b 的评分模型时使用）
     * @param zoneProperties       分区（品类 ↔ 巷道）映射
     * @param distanceMetric       距离口径
     * @param layerHeight          单层折算高度（三维折线口径使用）
     * @param randomSeed           随机种子，保证「随机分配」结果可复现
     * @param baseOccupiedSnapshot 仿真开始前的真实占用坐标快照（key = "x,y,layer"）
     */
    public SimulationContext(String simulationId, Warehouse warehouse, List<Rack> racks,
                             StorageRuleConfig rules, ScoreWeightConfig weights,
                             ZoneProperties zoneProperties, DistanceMetric distanceMetric,
                             double layerHeight, long randomSeed,
                             Map<String, Boolean> baseOccupiedSnapshot) {
        this.simulationId = simulationId;
        this.warehouse = warehouse;
        this.rules = rules;
        this.weights = weights;
        this.zoneProperties = zoneProperties;
        this.distanceMetric = distanceMetric == null ? DistanceMetric.MANHATTAN : distanceMetric;
        this.layerHeight = layerHeight;
        this.random = new Random(randomSeed);
        this.baseOccupiedSnapshot = baseOccupiedSnapshot == null ? Map.of() : baseOccupiedSnapshot;
        this.aisleByRack = new HashMap<>();
        if (racks != null) {
            for (Rack rack : racks) {
                aisleByRack.put(rack.getId(), rack.getAisle());
            }
        }
    }

    /**
     * 获取仿真单号。
     *
     * @return 仿真单号，如 INB-001
     */
    public String getSimulationId() {
        return simulationId;
    }

    /**
     * 获取仓库。
     *
     * @return 仓库
     */
    public Warehouse getWarehouse() {
        return warehouse;
    }

    /**
     * 获取分层规则。
     *
     * @return 分层规则
     */
    public StorageRuleConfig getRules() {
        return rules;
    }

    /**
     * 获取评分权重。
     *
     * @return 评分权重
     */
    public ScoreWeightConfig getWeights() {
        return weights;
    }

    /**
     * 获取距离口径。
     *
     * @return 距离口径
     */
    public DistanceMetric getDistanceMetric() {
        return distanceMetric;
    }

    /**
     * 获取出库口坐标（距离计算的终点，COM-6）。
     *
     * @return 出库口坐标
     */
    public Point getExitPoint() {
        int x = warehouse.getExitX() == null ? 0 : warehouse.getExitX();
        int y = warehouse.getExitY() == null ? 0 : warehouse.getExitY();
        return new Point(x, y);
    }

    /**
     * 出库口层号（预留给三维折线口径）。
     *
     * @return 出库口层号，默认 1
     */
    public int getExitLayer() {
        return warehouse.getExitLayer() == null ? 1 : warehouse.getExitLayer();
    }

    /**
     * 库位到出库口的搬运距离（统一口径，所有策略与统计都走这里）。
     *
     * @param location 库位
     * @return 搬运距离
     */
    public double distanceToExit(Location location) {
        return DistanceUtil.between(distanceMetric, DistanceUtil.pointOf(location),
                DistanceUtil.layerOf(location), getExitPoint(), getExitLayer(), layerHeight);
    }

    /**
     * 两个库位之间的搬运距离。
     *
     * @param from 起点库位
     * @param to   终点库位
     * @return 搬运距离
     */
    public double distanceBetween(Location from, Location to) {
        return DistanceUtil.between(distanceMetric, from, to, layerHeight);
    }

    /**
     * 取库位所属分区（品类）。
     *
     * <p>优先用仓储层已派生好的 {@code location.category}；若为空则按 {@code racks.aisle}
     * 现场派生，保证策略在两种数据来源（MySQL / 内存）下行为一致。
     *
     * @param location 库位
     * @return 分区品类，未配置映射时为 null
     */
    public String zoneOf(Location location) {
        if (location.getCategory() != null && !location.getCategory().isBlank()) {
            return location.getCategory();
        }
        return zoneProperties.categoryOfAisle(aisleByRack.get(location.getRackId()));
    }

    /**
     * 判断库位在本次仿真中是否仍可用（未被占用、且未在仿真过程中被选走）。
     *
     * @param location 库位
     * @return 可用返回 true
     */
    public boolean isAvailable(Location location) {
        return location.isFree() && !occupiedLocationIds.contains(location.getId());
    }

    /**
     * 把库位标记为本次仿真已占用，保证「一个库位同一时刻最多一种货物」
     * （《需求文档》4.4 约束一）在试算过程中也成立。
     *
     * @param location 被选中的库位
     */
    public void markOccupied(Location location) {
        occupiedLocationIds.add(location.getId());
        simulatedOccupied.put(coordKey(location), Boolean.TRUE);
    }

    /**
     * 当前占用快照：仿真前的真实占用 + 仿真过程中新占用的库位。
     *
     * <p>交给 b 的评分模型计算「空位连续性」——只有这样，一次仿真里连续放入的货物
     * 才会被认为「填充了连续空位」，而不是全部漏算。
     *
     * @return key = {@code "x,y,layer"} 的占用坐标集合
     */
    public Map<String, Boolean> occupiedSnapshot() {
        if (simulatedOccupied.isEmpty()) {
            return baseOccupiedSnapshot;
        }
        Map<String, Boolean> merged = new HashMap<>(baseOccupiedSnapshot);
        merged.putAll(simulatedOccupied);
        return merged;
    }

    /**
     * 占用快照的坐标键，与 {@code ScoringContext#continuity} 的取值口径一致。
     *
     * @param location 库位
     * @return {@code "x,y,layer"} 键
     */
    private static String coordKey(Location location) {
        int x = location.getX() == null ? 0 : location.getX();
        int y = location.getY() == null ? 0 : location.getY();
        int layer = location.getLayer() == null ? 0 : location.getLayer();
        return x + "," + y + "," + layer;
    }

    /**
     * 获取随机源（仅「随机分配」策略使用，种子固定以便复现结果）。
     *
     * @return 随机源
     */
    public Random getRandom() {
        return random;
    }
}
