package com.wms.domain.repository;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Plan;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基础数据访问接口（数据访问层 seam）。
 *
 * <p>本接口为成员 b 依赖的「公开数据接口」：仓库/库位/货物/方案由成员 a（COM-2，MyBatis-Plus）
 * 与成员 c（方案数据）提供。b 只通过本接口读取基础数据、更新库位占用状态，不直接访问数据表
 * （对应《代码规范》10.3「只通过公开 Service/接口调用」）。</p>
 *
 * <p>本切片内置 {@link InMemoryWarehouseDataRepository} 内存实现用于独立运行与单测；
 * 联调时由 a 提供 MyBatis-Plus 实现替换。</p>
 */
public interface WarehouseDataRepository {

    Optional<Warehouse> findWarehouse(Long id);

    List<Warehouse> listWarehouses();

    Optional<Sku> findSku(Long id);

    List<Sku> listSkus();

    List<Location> listLocations(Long warehouseId);

    List<Location> listFreeLocations(Long warehouseId);

    Optional<Location> findLocation(Long id);

    /** 更新库位占用状态（采用推荐时置为 occupied）。 */
    void updateLocationStatus(Long locationId, String status);

    /** 全仓库占用快照：key = "x,y,layer"，供空位连续性计算。 */
    Map<String, Boolean> occupiedSnapshot();

    List<Plan> listPlans();

    Optional<Plan> findPlan(Long id);
}
