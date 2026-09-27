package com.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 货架实体，对应表 {@code racks}。
 *
 * <p>库位编码规则为「巷道-货架序号-列-层」，如 {@code A-01-03-02}；
 * 本表声明货架规模（{@code column_count} / {@code layer_count}），
 * 库位实例存于 {@code locations} 表。
 *
 * @author a
 */
@TableName("racks")
public class Rack {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属仓库 id。 */
    private Long warehouseId;

    /** 货架编码，仓库内唯一，如 A-01。 */
    private String code;

    /** 巷道（A/B/C...），库位编码第 1 段。 */
    private String aisle;

    /** 列数，库位编码第 3 段。 */
    private Integer columnCount;

    /** 层数，库位编码第 4 段。 */
    private Integer layerCount;

    /** 货架基准平面坐标 x（平面图渲染）。 */
    private Integer x;

    /** 货架基准平面坐标 y（平面图渲染）。 */
    private Integer y;

    /** 库位排布方向：row 沿 x 递增 / column 沿 y 递增。 */
    private String orientation;

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
     * 获取货架编码，仓库内唯一，如 A-01。
     *
     * @return 货架编码，仓库内唯一，如 A-01
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置货架编码，仓库内唯一，如 A-01。
     *
     * @param code 货架编码，仓库内唯一，如 A-01
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 获取巷道（A/B/C...），库位编码第 1 段。
     *
     * @return 巷道（A/B/C...），库位编码第 1 段
     */
    public String getAisle() {
        return aisle;
    }

    /**
     * 设置巷道（A/B/C...），库位编码第 1 段。
     *
     * @param aisle 巷道（A/B/C...），库位编码第 1 段
     */
    public void setAisle(String aisle) {
        this.aisle = aisle;
    }

    /**
     * 获取列数，库位编码第 3 段。
     *
     * @return 列数，库位编码第 3 段
     */
    public Integer getColumnCount() {
        return columnCount;
    }

    /**
     * 设置列数，库位编码第 3 段。
     *
     * @param columnCount 列数，库位编码第 3 段
     */
    public void setColumnCount(Integer columnCount) {
        this.columnCount = columnCount;
    }

    /**
     * 获取层数，库位编码第 4 段。
     *
     * @return 层数，库位编码第 4 段
     */
    public Integer getLayerCount() {
        return layerCount;
    }

    /**
     * 设置层数，库位编码第 4 段。
     *
     * @param layerCount 层数，库位编码第 4 段
     */
    public void setLayerCount(Integer layerCount) {
        this.layerCount = layerCount;
    }

    /**
     * 获取货架基准平面坐标 x（平面图渲染）。
     *
     * @return 货架基准平面坐标 x（平面图渲染）
     */
    public Integer getX() {
        return x;
    }

    /**
     * 设置货架基准平面坐标 x（平面图渲染）。
     *
     * @param x 货架基准平面坐标 x（平面图渲染）
     */
    public void setX(Integer x) {
        this.x = x;
    }

    /**
     * 获取货架基准平面坐标 y（平面图渲染）。
     *
     * @return 货架基准平面坐标 y（平面图渲染）
     */
    public Integer getY() {
        return y;
    }

    /**
     * 设置货架基准平面坐标 y（平面图渲染）。
     *
     * @param y 货架基准平面坐标 y（平面图渲染）
     */
    public void setY(Integer y) {
        this.y = y;
    }

    /**
     * 获取库位排布方向：row 沿 x 递增 / column 沿 y 递增。
     *
     * @return 库位排布方向：row 沿 x 递增 / column 沿 y 递增
     */
    public String getOrientation() {
        return orientation;
    }

    /**
     * 设置库位排布方向：row 沿 x 递增 / column 沿 y 递增。
     *
     * @param orientation 库位排布方向：row 沿 x 递增 / column 沿 y 递增
     */
    public void setOrientation(String orientation) {
        this.orientation = orientation;
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
