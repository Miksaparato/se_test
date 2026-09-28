package com.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.domain.entity.type.LocationOccupancy;
import com.wms.domain.entity.type.PlanResult;
import com.wms.domain.handler.JsonMapTypeHandler;
import com.wms.domain.handler.LocationMapTypeHandler;
import com.wms.domain.handler.PlanResultTypeHandler;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 库位分配方案实体，对应表 {@code plans}（c 写、b 读）。
 *
 * <p>《数据库设计说明书》2.6 冻结的三段 JSON 结构在此显式映射：
 * <ul>
 *   <li>{@code params}：策略参数快照（自由结构）；</li>
 *   <li>{@code location_map}：库位占用映射 {@code {"库位id": {"skuId":3,"quantity":20}}}；</li>
 *   <li>{@code result}：仿真统计结果，字段名与 API-051 对比指标逐一对齐。</li>
 * </ul>
 *
 * <p>对外接口（API-049/050/051）的字段名保持不变：{@code planId/totalDistance/...} 由
 * {@link #getTotalDistance()} 等便捷访问器从 {@code result} 中透出，前端无需感知 JSON 嵌套。
 *
 * @author c
 */
@TableName(value = "plans", autoResultMap = true)
public class Plan {

    /** 主键（= 《接口文档》示例中的 {@code planId}）。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 方案编号，全局唯一，如 PLAN-20260910-001。 */
    private String planNo;

    /** 方案名称，如「随机分配-50单」。 */
    private String name;

    /** 所属仓库 id。 */
    private Long warehouseId;

    /** 产出该方案的仿真单号，对应接口字段 {@code simulationId}（如 INB-001）。 */
    private String simulationId;

    /** 策略标识：random / nearest / zoning / grading / smart / fifo。 */
    private String strategy;

    /** 策略中文展示名，如「智能推荐」。 */
    private String strategyName;

    /** 策略参数快照（JSON 列）。 */
    @TableField(value = "params", typeHandler = JsonMapTypeHandler.class)
    private Map<String, Object> params;

    /** 库位占用映射（JSON 列）：库位 id → 占用明细。 */
    @TableField(value = "location_map", typeHandler = LocationMapTypeHandler.class)
    private Map<String, LocationOccupancy> locationMap;

    /** 仿真统计结果（JSON 列）。 */
    @TableField(value = "result", typeHandler = PlanResultTypeHandler.class)
    private PlanResult result;

    /** 创建人用户 id。 */
    private Long createdBy;

    /** 创建时间(UTC)，插入时自动填充。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间(UTC)，插入与更新时自动填充。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** MyBatis-Plus 反射实例化所需的无参构造。 */
    public Plan() {
    }

    /**
     * 便捷构造：用于单元测试与内存演示实现（无编号/仓库等非空列，仅供内存场景）。
     *
     * @param id             主键
     * @param name           方案名称
     * @param strategy       策略中文名，同时作为策略标识
     * @param totalDistance  总搬运路程
     * @param avgDistance    平均每单路程
     * @param hotAvgDistance 高频货平均距离
     * @param violations     重货层位违规数
     */
    public Plan(Long id, String name, String strategy, double totalDistance,
                double avgDistance, double hotAvgDistance, int violations) {
        this.id = id;
        this.name = name;
        this.strategy = strategy;
        this.strategyName = strategy;
        this.result = new PlanResult(totalDistance, avgDistance, hotAvgDistance, violations, 0, 0.0);
    }

    /**
     * 获取主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键。
     *
     * @param id 主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取方案编号。
     *
     * @return 方案编号
     */
    public String getPlanNo() {
        return planNo;
    }

    /**
     * 设置方案编号。
     *
     * @param planNo 方案编号
     */
    public void setPlanNo(String planNo) {
        this.planNo = planNo;
    }

    /**
     * 获取方案名称。
     *
     * @return 方案名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置方案名称。
     *
     * @param name 方案名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取所属仓库 id。
     *
     * @return 所属仓库 id
     */
    public Long getWarehouseId() {
        return warehouseId;
    }

    /**
     * 设置所属仓库 id。
     *
     * @param warehouseId 所属仓库 id
     */
    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    /**
     * 获取产出该方案的仿真单号。
     *
     * @return 仿真单号，如 INB-001
     */
    public String getSimulationId() {
        return simulationId;
    }

    /**
     * 设置产出该方案的仿真单号。
     *
     * @param simulationId 仿真单号
     */
    public void setSimulationId(String simulationId) {
        this.simulationId = simulationId;
    }

    /**
     * 获取策略标识。
     *
     * @return 策略标识（random/nearest/zoning/grading/smart/fifo）
     */
    public String getStrategy() {
        return strategy;
    }

    /**
     * 设置策略标识。
     *
     * @param strategy 策略标识
     */
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    /**
     * 获取策略中文展示名。
     *
     * @return 策略中文展示名
     */
    public String getStrategyName() {
        return strategyName;
    }

    /**
     * 设置策略中文展示名。
     *
     * @param strategyName 策略中文展示名
     */
    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    /**
     * 获取策略参数快照。
     *
     * @return 参数映射，可能为 null
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * 设置策略参数快照。
     *
     * @param params 参数映射
     */
    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    /**
     * 获取库位占用映射（只读视图）。
     *
     * @return 库位 id 字符串 → 占用明细；无映射时返回空 Map
     */
    public Map<String, LocationOccupancy> getLocationMap() {
        return locationMap == null ? Map.of() : Collections.unmodifiableMap(locationMap);
    }

    /**
     * 设置库位占用映射。
     *
     * @param locationMap 库位 id 字符串 → 占用明细
     */
    public void setLocationMap(Map<String, LocationOccupancy> locationMap) {
        this.locationMap = locationMap == null ? null : new LinkedHashMap<>(locationMap);
    }

    /**
     * 获取仿真统计结果。
     *
     * @return 统计结果；未落库时为 {@link PlanResult#EMPTY}
     */
    public PlanResult getResult() {
        return result == null ? PlanResult.EMPTY : result;
    }

    /**
     * 设置仿真统计结果。
     *
     * @param result 统计结果
     */
    public void setResult(PlanResult result) {
        this.result = result;
    }

    /**
     * 获取创建人 id。
     *
     * @return 创建人 id
     */
    public Long getCreatedBy() {
        return createdBy;
    }

    /**
     * 设置创建人 id。
     *
     * @param createdBy 创建人 id
     */
    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * 获取创建时间(UTC)。
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间(UTC)。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取更新时间(UTC)。
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间(UTC)。
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ---------- 以下访问器从 result（JSON 列）透出对比指标，保持 API-051 字段名不变 ----------

    /**
     * 获取总搬运路程（透出自 {@code result.totalDistance}）。
     *
     * @return 总搬运路程
     */
    public double getTotalDistance() {
        return getResult().totalDistance();
    }

    /**
     * 获取平均每单路程（透出自 {@code result.avgDistance}）。
     *
     * @return 平均每单路程
     */
    public double getAvgDistance() {
        return getResult().avgDistance();
    }

    /**
     * 获取高频货平均距离（透出自 {@code result.hotAvgDistance}）。
     *
     * @return 高频货平均距离
     */
    public double getHotAvgDistance() {
        return getResult().hotAvgDistance();
    }

    /**
     * 获取重货层位违规数（透出自 {@code result.violations}）。
     *
     * @return 重货层位违规数
     */
    public int getViolations() {
        return getResult().violations();
    }

    /**
     * 获取参与出库仿真的订单数（透出自 {@code result.orderCount}）。
     *
     * @return 订单数
     */
    public int getOrderCount() {
        return getResult().orderCount();
    }

    /**
     * 获取出库耗时估算（透出自 {@code result.estimatedTime}）。
     *
     * @return 耗时估算（秒）
     */
    public double getEstimatedTime() {
        return getResult().estimatedTime();
    }
}
