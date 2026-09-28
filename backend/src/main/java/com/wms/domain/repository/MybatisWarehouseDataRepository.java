package com.wms.domain.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.config.ZoneProperties;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Plan;
import com.wms.domain.entity.Rack;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import com.wms.domain.mapper.LocationMapper;
import com.wms.domain.mapper.PlanMapper;
import com.wms.domain.mapper.RackMapper;
import com.wms.domain.mapper.SkuMapper;
import com.wms.domain.mapper.WarehouseMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@link WarehouseDataRepository} 的 MyBatis-Plus 实现：数据来自 MySQL（默认实现）。
 *
 * <p>性能约定（NFR-1：单仓库 1 万库位推荐 ≤ 5s）：
 * <ul>
 *   <li>库位一次查全（依赖 {@code idx_locations_warehouse_status}），不在循环里逐条查询；</li>
 *   <li>分区品类由货架巷道**一次批量**派生，货架只查一次，避免 N+1。</li>
 * </ul>
 *
 * <p>可通过 {@code wms.repository=memory} 切换到 {@link InMemoryWarehouseDataRepository}。
 *
 * @author a
 */
@Repository
@ConditionalOnProperty(name = "wms.repository", havingValue = "mysql", matchIfMissing = true)
public class MybatisWarehouseDataRepository implements WarehouseDataRepository {

    /** 库位状态：空闲。 */
    private static final String STATUS_FREE = "free";

    /** 库位状态：占用。 */
    private static final String STATUS_OCCUPIED = "occupied";

    private final WarehouseMapper warehouseMapper;
    private final RackMapper rackMapper;
    private final LocationMapper locationMapper;
    private final SkuMapper skuMapper;
    private final PlanMapper planMapper;
    private final ZoneProperties zoneProperties;

    /**
     * 构造仓储实现。
     *
     * @param warehouseMapper 仓库 Mapper
     * @param rackMapper      货架 Mapper
     * @param locationMapper  库位 Mapper
     * @param skuMapper       货物 Mapper
     * @param planMapper      方案 Mapper
     * @param zoneProperties  分区（品类 ↔ 巷道）映射配置
     */
    public MybatisWarehouseDataRepository(WarehouseMapper warehouseMapper,
                                          RackMapper rackMapper,
                                          LocationMapper locationMapper,
                                          SkuMapper skuMapper,
                                          PlanMapper planMapper,
                                          ZoneProperties zoneProperties) {
        this.warehouseMapper = warehouseMapper;
        this.rackMapper = rackMapper;
        this.locationMapper = locationMapper;
        this.skuMapper = skuMapper;
        this.planMapper = planMapper;
        this.zoneProperties = zoneProperties;
    }

    @Override
    public Optional<Warehouse> findWarehouse(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(warehouseMapper.selectById(id));
    }

    @Override
    public List<Warehouse> listWarehouses() {
        return warehouseMapper.selectList(Wrappers.<Warehouse>lambdaQuery()
                .orderByAsc(Warehouse::getId));
    }

    @Override
    public Optional<Sku> findSku(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(skuMapper.selectById(id));
    }

    @Override
    public List<Sku> listSkus() {
        return skuMapper.selectList(Wrappers.<Sku>lambdaQuery().orderByAsc(Sku::getId));
    }

    @Override
    public List<Location> listLocations(Long warehouseId) {
        if (warehouseId == null) {
            return List.of();
        }
        List<Location> locations = locationMapper.selectList(Wrappers.<Location>lambdaQuery()
                .eq(Location::getWarehouseId, warehouseId)
                .orderByAsc(Location::getId));
        fillZoneCategory(locations, warehouseId);
        return locations;
    }

    @Override
    public List<Location> listFreeLocations(Long warehouseId) {
        if (warehouseId == null) {
            return List.of();
        }
        List<Location> locations = locationMapper.selectList(Wrappers.<Location>lambdaQuery()
                .eq(Location::getWarehouseId, warehouseId)
                .eq(Location::getStatus, STATUS_FREE)
                .orderByAsc(Location::getId));
        fillZoneCategory(locations, warehouseId);
        return locations;
    }

    @Override
    public Optional<Location> findLocation(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        Location location = locationMapper.selectById(id);
        if (location != null) {
            fillZoneCategory(List.of(location), location.getWarehouseId());
        }
        return Optional.ofNullable(location);
    }

    @Override
    public Optional<Rack> findRack(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(rackMapper.selectById(id));
    }

    @Override
    public List<Rack> listRacks(Long warehouseId) {
        if (warehouseId == null) {
            return List.of();
        }
        return rackMapper.selectList(Wrappers.<Rack>lambdaQuery()
                .eq(Rack::getWarehouseId, warehouseId)
                .orderByAsc(Rack::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void occupyLocation(Long locationId, Long skuId) {
        if (locationId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "库位 id 不能为空");
        }
        Location existing = locationMapper.selectById(locationId);
        if (existing == null) {
            throw new BizException(ErrorCode.LOCATION_NOT_FOUND, "库位不存在: " + locationId);
        }
        if (!STATUS_FREE.equals(existing.getStatus())) {
            throw new BizException(ErrorCode.LOCATION_OCCUPIED, "库位 " + existing.getCode() + " 已被占用");
        }
        // 条件更新：仅当仍为 free 时生效，避免并发下一库位两货（约束 4.4 一库位一货物）
        int affected = locationMapper.update(null, Wrappers.<Location>lambdaUpdate()
                .eq(Location::getId, locationId)
                .eq(Location::getStatus, STATUS_FREE)
                .set(Location::getStatus, STATUS_OCCUPIED)
                .set(Location::getOccupiedSkuId, skuId));
        if (affected == 0) {
            throw new BizException(ErrorCode.LOCATION_OCCUPIED, "库位 " + existing.getCode() + " 已被占用");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseLocation(Long locationId) {
        if (locationId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "库位 id 不能为空");
        }
        Location existing = locationMapper.selectById(locationId);
        if (existing == null) {
            throw new BizException(ErrorCode.LOCATION_NOT_FOUND, "库位不存在: " + locationId);
        }
        locationMapper.update(null, Wrappers.<Location>lambdaUpdate()
                .eq(Location::getId, locationId)
                .set(Location::getStatus, STATUS_FREE)
                .set(Location::getOccupiedSkuId, null));
    }

    @Override
    public Map<String, Boolean> occupiedSnapshot(Long warehouseId) {
        Map<String, Boolean> snapshot = new HashMap<>();
        if (warehouseId == null) {
            return snapshot;
        }
        List<Location> occupied = locationMapper.selectList(Wrappers.<Location>lambdaQuery()
                .select(Location::getX, Location::getY, Location::getLayer, Location::getStatus)
                .eq(Location::getWarehouseId, warehouseId)
                .ne(Location::getStatus, STATUS_FREE));
        for (Location l : occupied) {
            snapshot.put(coordKey(l.getX(), l.getY(), l.getLayer()), Boolean.TRUE);
        }
        return snapshot;
    }

    @Override
    public List<Plan> listPlans(Long warehouseId) {
        return planMapper.selectList(Wrappers.<Plan>lambdaQuery()
                .eq(warehouseId != null, Plan::getWarehouseId, warehouseId)
                .orderByDesc(Plan::getId));
    }

    @Override
    public Optional<Plan> findPlan(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(planMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Plan savePlan(Plan plan) {
        if (plan == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "方案不能为空");
        }
        if (plan.getId() == null) {
            planMapper.insert(plan);
        } else {
            planMapper.updateById(plan);
        }
        return plan;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePlan(Long planId) {
        return planId != null && planMapper.deleteById(planId) > 0;
    }

    @Override
    public Optional<String> findMaxPlanNo(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Optional.empty();
        }
        Plan latest = planMapper.selectOne(Wrappers.<Plan>lambdaQuery()
                .select(Plan::getPlanNo)
                .likeRight(Plan::getPlanNo, prefix)
                .orderByDesc(Plan::getPlanNo)
                .last("LIMIT 1"));
        return latest == null ? Optional.empty() : Optional.ofNullable(latest.getPlanNo());
    }

    /**
     * 批量派生库位的分区品类：货架巷道 → 品类（可配置映射）。
     *
     * <p>货架只查一次并建成 Map，避免逐库位查询货架（N+1，《代码规范》4.4）。
     *
     * @param locations   待填充的库位（原地修改）
     * @param warehouseId 所属仓库 id
     */
    private void fillZoneCategory(List<Location> locations, Long warehouseId) {
        if (locations == null || locations.isEmpty() || warehouseId == null) {
            return;
        }
        Map<String, String> aisleCategory = zoneProperties.getAisleCategory();
        if (aisleCategory.isEmpty()) {
            return;
        }
        Set<Long> rackIds = locations.stream()
                .map(Location::getRackId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (rackIds.isEmpty()) {
            return;
        }
        Map<Long, Rack> racks = rackMapper.selectBatchIds(rackIds).stream()
                .collect(Collectors.toMap(Rack::getId, Function.identity(), (a, b) -> a));
        for (Location location : locations) {
            Rack rack = racks.get(location.getRackId());
            if (rack != null) {
                location.setCategory(zoneProperties.categoryOfAisle(rack.getAisle()));
            }
        }
    }

    /**
     * 占用快照的坐标键，与 {@code ScoringContext#continuity} 的取值口径一致。
     *
     * @param x     平面坐标 x
     * @param y     平面坐标 y
     * @param layer 层号
     * @return {@code "x,y,layer"} 键
     */
    private static String coordKey(Integer x, Integer y, Integer layer) {
        return (x == null ? 0 : x) + "," + (y == null ? 0 : y) + "," + (layer == null ? 0 : layer);
    }
}
