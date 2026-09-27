package com.wms.domain.repository;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.config.ZoneProperties;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Plan;
import com.wms.domain.entity.Rack;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link WarehouseDataRepository} 的内存实现（**演示/离线模式**）。
 *
 * <p>仅在 {@code wms.repository=memory} 时启用（默认走 MySQL 的
 * {@link MybatisWarehouseDataRepository}）。用途：
 * <ul>
 *   <li>无 MySQL 环境下独立启动演示推荐/仿真（README 的「可独立运行」场景）；</li>
 *   <li>算法单测直接用实体构造数据时不经过 Spring，本类不参与。</li>
 * </ul>
 *
 * <p>构造时内置演示数据：1 个仓库、3 个货架、36 个库位、5 个货物、3 个方案。
 *
 * @author a
 */
@Component
@ConditionalOnProperty(name = "wms.repository", havingValue = "memory")
public class InMemoryWarehouseDataRepository implements WarehouseDataRepository {

    /** 演示仓库 id。 */
    public static final long DEMO_WAREHOUSE_ID = 1L;

    private static final String STATUS_FREE = "free";
    private static final String STATUS_OCCUPIED = "occupied";

    private final Map<Long, Warehouse> warehouses = new ConcurrentHashMap<>();
    private final Map<Long, Rack> racks = new ConcurrentHashMap<>();
    private final Map<Long, Location> locations = new ConcurrentHashMap<>();
    private final Map<Long, Sku> skus = new ConcurrentHashMap<>();
    private final Map<Long, Plan> plans = new ConcurrentHashMap<>();
    private final AtomicLong planSeq = new AtomicLong(100L);

    private final ZoneProperties zoneProperties;

    /**
     * 构造并写入演示数据。
     *
     * @param zoneProperties 分区（品类 ↔ 巷道）映射配置，用于给内存库位派生品类
     */
    public InMemoryWarehouseDataRepository(ZoneProperties zoneProperties) {
        this.zoneProperties = zoneProperties;
        seed();
    }

    private void seed() {
        Warehouse w = new Warehouse(DEMO_WAREHOUSE_ID, "一号仓", 0, 0);
        w.setCode("WH-DEMO");
        warehouses.put(w.getId(), w);

        // 3 个货架（巷道 A/B/C），每个 4 列 × 3 层 = 12 个库位，共 36 个
        String[] aisles = {"A", "B", "C"};
        long rackId = 1L;
        long locationId = 1001L;
        for (int r = 0; r < aisles.length; r++) {
            String aisle = aisles[r];
            Rack rack = new Rack();
            rack.setId(rackId);
            rack.setWarehouseId(w.getId());
            rack.setCode(aisle + "-01");
            rack.setAisle(aisle);
            rack.setColumnCount(4);
            rack.setLayerCount(3);
            rack.setX(r * 4 + 2);
            rack.setY(2);
            rack.setOrientation("row");
            racks.put(rackId, rack);

            for (int col = 1; col <= 4; col++) {
                for (int layer = 1; layer <= 3; layer++) {
                    int x = (r * 4) + col * 2;
                    int y = 2 + (col - 1) * 2;
                    Location l = new Location(locationId,
                            String.format("%s-%02d-%02d-%02d", aisle, rackId, col, layer),
                            x, y, layer, w.getId());
                    l.setRackId(rackId);
                    l.setCapacity(BigDecimal.valueOf(100));
                    l.setCategory(zoneProperties.categoryOfAisle(aisle));
                    // 约 1/3 预置为占用，用于演示空位连续性与空闲池
                    if (locationId % 3 == 0) {
                        l.setStatus(STATUS_OCCUPIED);
                    }
                    locations.put(locationId, l);
                    locationId++;
                }
            }
            rackId++;
        }

        skus.put(1L, new Sku(1L, "SKU-001", "高频电子元件", 50, 0.9, 5, "电子"));
        skus.put(2L, new Sku(2L, "SKU-002", "重型机械", 180, 0.2, 3, "机械"));
        skus.put(3L, new Sku(3L, "SKU-003", "服装", 8, 0.6, 2, "服装"));
        skus.put(4L, new Sku(4L, "SKU-004", "食品", 12, 0.7, 4, "食品"));
        skus.put(5L, new Sku(5L, "SKU-005", "五金配件", 90, 0.4, 3, "机械"));

        plans.put(10L, new Plan(10L, "随机分配", "random", 5820, 116.4, 210.3, 5));
        plans.put(11L, new Plan(11L, "就近分配", "nearest", 4310, 86.2, 98.1, 0));
        plans.put(12L, new Plan(12L, "智能推荐", "smart", 4755, 95.1, 76.4, 0));
    }

    @Override
    public Optional<Warehouse> findWarehouse(Long id) {
        return Optional.ofNullable(warehouses.get(id));
    }

    @Override
    public List<Warehouse> listWarehouses() {
        return new ArrayList<>(warehouses.values());
    }

    @Override
    public Optional<Sku> findSku(Long id) {
        return Optional.ofNullable(skus.get(id));
    }

    @Override
    public List<Sku> listSkus() {
        return new ArrayList<>(skus.values());
    }

    @Override
    public List<Location> listLocations(Long warehouseId) {
        return locations.values().stream()
                .filter(l -> warehouseId != null && warehouseId.equals(l.getWarehouseId()))
                .sorted(Comparator.comparing(Location::getId))
                .toList();
    }

    @Override
    public List<Location> listFreeLocations(Long warehouseId) {
        return listLocations(warehouseId).stream()
                .filter(Location::isFree)
                .toList();
    }

    @Override
    public Optional<Location> findLocation(Long id) {
        return Optional.ofNullable(locations.get(id));
    }

    @Override
    public Optional<Rack> findRack(Long id) {
        return Optional.ofNullable(racks.get(id));
    }

    @Override
    public List<Rack> listRacks(Long warehouseId) {
        return racks.values().stream()
                .filter(r -> warehouseId != null && warehouseId.equals(r.getWarehouseId()))
                .sorted(Comparator.comparing(Rack::getId))
                .toList();
    }

    @Override
    public void occupyLocation(Long locationId, Long skuId) {
        Location l = locations.get(locationId);
        if (l == null) {
            throw new BizException(ErrorCode.LOCATION_NOT_FOUND, "库位不存在: " + locationId);
        }
        if (!l.isFree()) {
            throw new BizException(ErrorCode.LOCATION_OCCUPIED, "库位 " + l.getCode() + " 已被占用");
        }
        l.setStatus(STATUS_OCCUPIED);
        l.setOccupiedSkuId(skuId);
    }

    @Override
    public void releaseLocation(Long locationId) {
        Location l = locations.get(locationId);
        if (l == null) {
            throw new BizException(ErrorCode.LOCATION_NOT_FOUND, "库位不存在: " + locationId);
        }
        l.setStatus(STATUS_FREE);
        l.setOccupiedSkuId(null);
    }

    @Override
    public Map<String, Boolean> occupiedSnapshot(Long warehouseId) {
        Map<String, Boolean> m = new HashMap<>();
        for (Location l : locations.values()) {
            if (warehouseId != null && warehouseId.equals(l.getWarehouseId()) && !l.isFree()) {
                m.put(l.getX() + "," + l.getY() + "," + l.getLayer(), Boolean.TRUE);
            }
        }
        return m;
    }

    @Override
    public List<Plan> listPlans(Long warehouseId) {
        return plans.values().stream()
                .filter(p -> warehouseId == null || warehouseId.equals(p.getWarehouseId())
                        || p.getWarehouseId() == null)
                .sorted(Comparator.comparing(Plan::getId).reversed())
                .toList();
    }

    @Override
    public Optional<Plan> findPlan(Long id) {
        return Optional.ofNullable(plans.get(id));
    }

    /**
     * 内存模式下的方案登记（MySQL 模式由 {@code PlanMapper} 落库）。
     *
     * @param plan 方案（id 为空时自动分配）
     * @return 登记后的方案
     */
    @Override
    public Plan savePlan(Plan plan) {
        if (plan.getId() == null) {
            plan.setId(planSeq.incrementAndGet());
        }
        if (plan.getWarehouseId() == null) {
            plan.setWarehouseId(DEMO_WAREHOUSE_ID);
        }
        plans.put(plan.getId(), plan);
        return plan;
    }

    /**
     * 删除内存方案（MySQL 模式由 {@code PlanMapper} 落库）。
     *
     * @param planId 方案 id
     * @return 删除成功返回 true
     */
    @Override
    public boolean deletePlan(Long planId) {
        return planId != null && plans.remove(planId) != null;
    }

    /**
     * 查询内存中指定前缀下最大的方案编号。
     *
     * @param prefix 编号前缀
     * @return 最大编号
     */
    @Override
    public Optional<String> findMaxPlanNo(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Optional.empty();
        }
        return plans.values().stream()
                .map(Plan::getPlanNo)
                .filter(no -> no != null && no.startsWith(prefix))
                .max(Comparator.naturalOrder());
    }
}
