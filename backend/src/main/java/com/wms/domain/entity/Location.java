package com.wms.domain.entity;

/**
 * 库位——对应《接口文档》API-021 / API-027 冻结字段。
 * 坐标 (x,y) 为二维平面坐标，layer 为层号（1 = 最底层）。
 */
public class Location {

    private Long id;
    /** 唯一编码，如 A-01-03-02。 */
    private String code;
    private int x;
    private int y;
    /** 层号，1 为最底层，越大越高。 */
    private int layer;
    /** 状态：free / occupied。 */
    private String status;
    /** 容量，预留尺寸校验。 */
    private double capacity;
    /** 分区品类（可空），用于「其他」分项中的品类匹配。 */
    private String category;
    private Long warehouseId;

    public Location() {
    }

    public Location(Long id, String code, int x, int y, int layer, Long warehouseId) {
        this.id = id;
        this.code = code;
        this.x = x;
        this.y = y;
        this.layer = layer;
        this.status = "free";
        this.warehouseId = warehouseId;
    }

    public boolean isFree() {
        return "free".equals(status);
    }

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库位实体，对应表 {@code locations}。
 *
 * <p><b>这是推荐引擎（b）与仿真引擎（c）的核心数据来源</b>，以下字段为冻结契约：
 * <ul>
 *   <li>{@code status}：free 空闲 / occupied 占用 / disabled 停用，推荐与仿真只消费 free；</li>
 *   <li>{@code layer}：层号从 1 开始，层号越小越靠地面（评分 S_weight 依据）；</li>
 *   <li>{@code x} / {@code y}：曼哈顿距离计算的起点（COM-6）；</li>
 *   <li>{@code capacity}：体积口径，与 SKU 尺寸校验；</li>
 *   <li>{@code warehouse_id}：冗余列，供 b、c 一次查出某仓库全部库位，无需 JOIN racks。</li>
 * </ul>
 *
 * @author a
 */
@TableName("locations")
public class Location {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属货架 id。 */
    private Long rackId;

    /** 所属仓库 id（冗余，由业务保证与货架一致）。 */
    private Long warehouseId;

    /** 库位唯一编码 巷道-货架序号-列-层，如 A-01-03-02，全局唯一。 */
    private String code;

    /** 平面坐标 x（曼哈顿距离起点）。 */
    private Integer x;

    /** 平面坐标 y（曼哈顿距离起点）。 */
    private Integer y;

    /** 层号，从 1 开始，层号越小越靠地面。 */
    private Integer layer;

    /** 状态：free 空闲 / occupied 占用 / disabled 停用。 */
    private String status;

    /** 库位容量（体积口径）。 */
    private BigDecimal capacity;

    /** 当前占用货物 id，status=occupied 时必须非空。 */
    private Long occupiedSkuId;

    /** 创建时间(UTC)，插入时自动填充。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间(UTC)，插入与更新时自动填充。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

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
     * 获取所属货架 id。
     *
     * @return 所属货架 id
     */
    public Long getRackId() {
        return rackId;
    }

    /**
     * 设置所属货架 id。
     *
     * @param rackId 所属货架 id
     */
    public void setRackId(Long rackId) {
        this.rackId = rackId;
    }

    /**
     * 获取所属仓库 id（冗余，由业务保证与货架一致）。
     *
     * @return 所属仓库 id（冗余，由业务保证与货架一致）
     */
    public Long getWarehouseId() {
        return warehouseId;
    }

    /**
     * 设置所属仓库 id（冗余，由业务保证与货架一致）。
     *
     * @param warehouseId 所属仓库 id（冗余，由业务保证与货架一致）
     */
    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    /**
     * 获取库位唯一编码 巷道-货架序号-列-层，如 A-01-03-02，全局唯一。
     *
     * @return 库位唯一编码 巷道-货架序号-列-层，如 A-01-03-02，全局唯一
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置库位唯一编码 巷道-货架序号-列-层，如 A-01-03-02，全局唯一。
     *
     * @param code 库位唯一编码 巷道-货架序号-列-层，如 A-01-03-02，全局唯一
     */
    public void setCode(String code) {
        this.code = code;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    /**
     * 获取平面坐标 x（曼哈顿距离起点）。
     *
     * @return 平面坐标 x（曼哈顿距离起点）
     */
    public Integer getX() {
        return x;
    }

    /**
     * 设置平面坐标 x（曼哈顿距离起点）。
     *
     * @param x 平面坐标 x（曼哈顿距离起点）
     */
    public void setX(Integer x) {
        this.x = x;
    }

    /**
     * 获取平面坐标 y（曼哈顿距离起点）。
     *
     * @return 平面坐标 y（曼哈顿距离起点）
     */
    public Integer getY() {
        return y;
    }

    /**
     * 设置平面坐标 y（曼哈顿距离起点）。
     *
     * @param y 平面坐标 y（曼哈顿距离起点）
     */
    public void setY(Integer y) {
        this.y = y;
    }

    /**
     * 获取层号，从 1 开始，层号越小越靠地面。
     *
     * @return 层号，从 1 开始，层号越小越靠地面
     */
    public Integer getLayer() {
        return layer;
    }

    /**
     * 设置层号，从 1 开始，层号越小越靠地面。
     *
     * @param layer 层号，从 1 开始，层号越小越靠地面
     */
    public void setLayer(Integer layer) {
        this.layer = layer;
    }

    /**
     * 获取状态：free 空闲 / occupied 占用 / disabled 停用。
     *
     * @return 状态：free 空闲 / occupied 占用 / disabled 停用
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置状态：free 空闲 / occupied 占用 / disabled 停用。
     *
     * @param status 状态：free 空闲 / occupied 占用 / disabled 停用
     */
    public void setStatus(String status) {
        this.status = status;
    }

    public double getCapacity() {
        return capacity;
    }

    public void setCapacity(double capacity) {
        this.capacity = capacity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }
    /**
     * 获取库位容量（体积口径）。
     *
     * @return 库位容量（体积口径）
     */
    public BigDecimal getCapacity() {
        return capacity;
    }

    /**
     * 设置库位容量（体积口径）。
     *
     * @param capacity 库位容量（体积口径）
     */
    public void setCapacity(BigDecimal capacity) {
        this.capacity = capacity;
    }

    /**
     * 获取当前占用货物 id，status=occupied 时必须非空。
     *
     * @return 当前占用货物 id，status=occupied 时必须非空
     */
    public Long getOccupiedSkuId() {
        return occupiedSkuId;
    }

    /**
     * 设置当前占用货物 id，status=occupied 时必须非空。
     *
     * @param occupiedSkuId 当前占用货物 id，status=occupied 时必须非空
     */
    public void setOccupiedSkuId(Long occupiedSkuId) {
        this.occupiedSkuId = occupiedSkuId;
    }

    /**
     * 获取创建时间(UTC)，插入时自动填充。
     *
     * @return 创建时间(UTC)，插入时自动填充
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间(UTC)，插入时自动填充。
     *
     * @param createdAt 创建时间(UTC)，插入时自动填充
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取更新时间(UTC)，插入与更新时自动填充。
     *
     * @return 更新时间(UTC)，插入与更新时自动填充
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间(UTC)，插入与更新时自动填充。
     *
     * @param updatedAt 更新时间(UTC)，插入与更新时自动填充
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}
