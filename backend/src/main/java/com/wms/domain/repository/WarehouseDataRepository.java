package com.wms.domain.repository;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Plan;
import com.wms.domain.entity.Rack;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基础数据访问接口（数据访问层 seam）。
 *
 * <p>本接口是 b（推荐引擎）与 c（仿真引擎）依赖的「公开数据接口」：仓库/货架/库位/货物/方案
 * 由成员 a（COM-2，MyBatis-Plus）与成员 c（方案数据）提供。b、c 只通过本接口读取基础数据、
 * 变更库位占用状态，不直接访问数据表（《代码规范》10.3「只通过公开 Service/接口调用」）。
 *
 * <p>实现：
 * <ul>
 *   <li>{@link MybatisWarehouseDataRepository}：默认实现，数据来自 MySQL（生产/联调路径）；</li>
 *   <li>{@link InMemoryWarehouseDataRepository}：内存实现，仅在 {@code wms.repository=memory}
 *       时启用，供无数据库环境独立演示与跑算法单测。</li>
 * </ul>
 *
 * @author a
 */
public interface WarehouseDataRepository {

    /**
     * 按 id 查询仓库。
     *
     * @param id 仓库 id
     * @return 仓库，不存在时为空
     */
    Optional<Warehouse> findWarehouse(Long id);

    /**
     * 查询全部仓库。
     *
     * @return 仓库列表
     */
    List<Warehouse> listWarehouses();

    /**
     * 按 id 查询货物。
     *
     * @param id 货物 id
     * @return 货物，不存在时为空
     */
    Optional<Sku> findSku(Long id);

    /**
     * 查询全部货物。
     *
     * @return 货物列表
     */
    List<Sku> listSkus();

    /**
     * 查询某仓库的全部库位（含停用），分区品类已派生填充。
     *
     * @param warehouseId 仓库 id
     * @return 库位列表
     */
    List<Location> listLocations(Long warehouseId);

    /**
     * 查询某仓库的空闲库位（{@code status = free}），供推荐与入库仿真消费。
     *
     * @param warehouseId 仓库 id
     * @return 空闲库位列表
     */
    List<Location> listFreeLocations(Long warehouseId);

    /**
     * 按 id 查询库位（分区品类已派生填充）。
     *
     * @param id 库位 id
     * @return 库位，不存在时为空
     */
    Optional<Location> findLocation(Long id);

    /**
     * 按 id 查询货架。
     *
     * @param id 货架 id
     * @return 货架，不存在时为空
     */
    Optional<Rack> findRack(Long id);

    /**
     * 查询某仓库的全部货架。
     *
     * @param warehouseId 仓库 id
     * @return 货架列表
     */
    List<Rack> listRacks(Long warehouseId);

    /**
     * 占用库位（采用推荐 / 入库仿真落库）。
     *
     * <p>《需求文档》4.4 约束一「一个库位同一时刻最多存放一种货物」：实现必须是
     * **条件更新**（仅当 {@code status = free} 时成功），否则并发下会一库位两货。
     *
     * @param locationId 库位 id
     * @param skuId      占用该库位的货物 id
     * @throws com.wms.common.BizException 库位不存在或已被占用（42201）
     */
    void occupyLocation(Long locationId, Long skuId);

    /**
     * 释放库位（清空占用货物并置为 free）。
     *
     * @param locationId 库位 id
     * @throws com.wms.common.BizException 库位不存在（40404）
     */
    void releaseLocation(Long locationId);

    /**
     * 某仓库的占用快照，key = {@code "x,y,layer"}，供评分模型的「空位连续性」计算。
     *
     * @param warehouseId 仓库 id
     * @return 占用坐标集合（value 恒为 true）
     */
    Map<String, Boolean> occupiedSnapshot(Long warehouseId);

    /**
     * 查询某仓库的分配方案列表。
     *
     * @param warehouseId 仓库 id，为 null 时返回全部方案
     * @return 方案列表（按创建时间倒序）
     */
    List<Plan> listPlans(Long warehouseId);

    /**
     * 按 id 查询分配方案。
     *
     * @param id 方案 id（对外 planId）
     * @return 方案，不存在时为空
     */
    Optional<Plan> findPlan(Long id);

    /**
     * 保存分配方案（由 c 的仿真引擎产出方案时调用）。
     *
     * @param plan 方案；主键为空时由实现自动生成
     * @return 落库/登记后的方案（含主键）
     */
    Plan savePlan(Plan plan);
}
