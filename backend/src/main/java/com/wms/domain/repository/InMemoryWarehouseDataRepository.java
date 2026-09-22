package com.wms.domain.repository;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Plan;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link WarehouseDataRepository} 的内存实现，用于成员 b 模块独立运行与单测。
 * 构造时内置演示数据（1 个仓库 + 若干库位 + 5 个 SKU + 3 个方案）。
 * 联调时由成员 a 以 MyBatis-Plus 实现替换（数据来自 MySQL）。
 */
@Component
public class InMemoryWarehouseDataRepository implements WarehouseDataRepository {

    private final Map<Long, Warehouse> warehouses = new ConcurrentHashMap<>();
    private final Map<Long, Location> locations = new ConcurrentHashMap<>();
    private final Map<Long, Sku> skus = new ConcurrentHashMap<>();
    private final Map<Long, Plan> plans = new ConcurrentHashMap<>();

    public InMemoryWarehouseDataRepository() {
        seed();
    }

    private void seed() {
        Warehouse w = new Warehouse(1L, "一号仓", 0, 0);
        warehouses.put(w.getId(), w);

        // 网格库位：4 列 × 3 行 × 3 层 = 36 个；x 为列方向，y 为行方向，layer 1 为最底层
        long id = 1001L;
        int col = 1;
        for (int x = 2; x <= 8; x += 2) {
            int row = 1;
            for (int y = 2; y <= 6; y += 2) {
                for (int layer = 1; layer <= 3; layer++) {
                    Location l = new Location(id,
                            String.format("A-%02d-%02d-%02d", col, row, layer),
                            x, y, layer, w.getId());
                    // 分区品类：x 小 → 电子区，x 大 → 食品区；部分无分区约束
                    if (x <= 4) {
                        l.setCategory("电子");
                    } else if (x >= 6 && col % 2 == 1) {
                        l.setCategory("食品");
                    }
                    l.setCapacity(100);
                    // 约 1/3 预置为占用，用于演示空位连续性与空闲池
                    if ((id % 3) == 0) {
                        l.setStatus("occupied");
                    }
                    locations.put(id, l);
                    id++;
                }
                row++;
            }
            col++;
        }

        skus.put(1L, new Sku(1L, "SKU-001", "高频电子元件", 50, 0.9, 5, "电子"));
        skus.put(2L, new Sku(2L, "SKU-002", "重型机械", 180, 0.2, 3, "机械"));
        skus.put(3L, new Sku(3L, "SKU-003", "服装", 8, 0.6, 2, "服装"));
        skus.put(4L, new Sku(4L, "SKU-004", "食品", 12, 0.7, 4, "食品"));
        skus.put(5L, new Sku(5L, "SKU-005", "五金配件", 90, 0.4, 3, "机械"));

        plans.put(10L, new Plan(10L, "随机分配", "随机分配", 5820, 116.4, 210.3, 5));
        plans.put(11L, new Plan(11L, "就近分配", "就近分配", 4310, 86.2, 98.1, 0));
        plans.put(12L, new Plan(12L, "智能推荐", "智能推荐", 4755, 95.1, 76.4, 0));
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
                .filter(l -> warehouseId.equals(l.getWarehouseId()))
                .toList();
    }

    @Override
    public List<Location> listFreeLocations(Long warehouseId) {
        return locations.values().stream()
                .filter(l -> warehouseId.equals(l.getWarehouseId()))
                .filter(Location::isFree)
                .toList();
    }

    @Override
    public Optional<Location> findLocation(Long id) {
        return Optional.ofNullable(locations.get(id));
    }

    @Override
    public void updateLocationStatus(Long locationId, String status) {
        Location l = locations.get(locationId);
        if (l != null) {
            l.setStatus(status);
        }
    }

    @Override
    public Map<String, Boolean> occupiedSnapshot() {
        Map<String, Boolean> m = new ConcurrentHashMap<>();
        for (Location l : locations.values()) {
            if (!l.isFree()) {
                m.put(coordKey(l.getX(), l.getY(), l.getLayer()), true);
            }
        }
        return m;
    }

    @Override
    public List<Plan> listPlans() {
        return new ArrayList<>(plans.values());
    }

    @Override
    public Optional<Plan> findPlan(Long id) {
        return Optional.ofNullable(plans.get(id));
    }

    private static String coordKey(int x, int y, int layer) {
        return x + "," + y + "," + layer;
    }
}
