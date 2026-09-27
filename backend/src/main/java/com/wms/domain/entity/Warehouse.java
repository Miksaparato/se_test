package com.wms.domain.entity;

/**
 * 仓库——对应《接口文档》API-021 冻结字段。
 * 出库口坐标 (exitX, exitY) 是距离/路程计算的原点（COM-6，曼哈顿距离，成员 c 牵头）。
 */
public class Warehouse {

    private Long id;
    private String name;
    private int exitX;
    private int exitY;

    public Warehouse() {
    }

    public Warehouse(Long id, String name, int exitX, int exitY) {
        this.id = id;
        this.name = name;
        this.exitX = exitX;
        this.exitY = exitY;
    }

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 仓库实体，对应表 {@code warehouses}。
 *
 * <p>出库口坐标（{@code exit_x} / {@code exit_y}）是曼哈顿距离计算的终点（COM-6，由 c 牵头统一口径），
 * 在 API-021 仓库平面布局中以 {@code exit {x, y}} 返回。
 *
 * @author a
 */
@TableName("warehouses")
public class Warehouse {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 仓库编码，全局唯一，如 WH-01。 */
    private String code;

    /** 仓库名称，如 一号仓。 */
    private String name;

    /** 仓库长（米/格），FR-1.1。 */
    private Integer length;

    /** 仓库宽（米/格），FR-1.1。 */
    private Integer width;

    /** 仓库高 / 最大层数，FR-1.1。 */
    private Integer height;

    /** 出库口平面坐标 x，距离计算终点。 */
    private Integer exitX;

    /** 出库口平面坐标 y，距离计算终点。 */
    private Integer exitY;

    /** 出库口层号，默认 1（预留给三维扩展）。 */
    private Integer exitLayer;

    /** 备注。 */
    private String remark;

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
     * 获取仓库编码，全局唯一，如 WH-01。
     *
     * @return 仓库编码，全局唯一，如 WH-01
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置仓库编码，全局唯一，如 WH-01。
     *
     * @param code 仓库编码，全局唯一，如 WH-01
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 获取仓库名称，如 一号仓。
     *
     * @return 仓库名称，如 一号仓
     */
    public String getName() {
        return name;
    }

    /**
     * 设置仓库名称，如 一号仓。
     *
     * @param name 仓库名称，如 一号仓
     */
    public void setName(String name) {
        this.name = name;
    }

    public int getExitX() {
        return exitX;
    }

    public void setExitX(int exitX) {
        this.exitX = exitX;
    }

    public int getExitY() {
        return exitY;
    }

    public void setExitY(int exitY) {
        this.exitY = exitY;
    }
    /**
     * 获取仓库长（米/格），FR-1.1。
     *
     * @return 仓库长（米/格），FR-1.1
     */
    public Integer getLength() {
        return length;
    }

    /**
     * 设置仓库长（米/格），FR-1.1。
     *
     * @param length 仓库长（米/格），FR-1.1
     */
    public void setLength(Integer length) {
        this.length = length;
    }

    /**
     * 获取仓库宽（米/格），FR-1.1。
     *
     * @return 仓库宽（米/格），FR-1.1
     */
    public Integer getWidth() {
        return width;
    }

    /**
     * 设置仓库宽（米/格），FR-1.1。
     *
     * @param width 仓库宽（米/格），FR-1.1
     */
    public void setWidth(Integer width) {
        this.width = width;
    }

    /**
     * 获取仓库高 / 最大层数，FR-1.1。
     *
     * @return 仓库高 / 最大层数，FR-1.1
     */
    public Integer getHeight() {
        return height;
    }

    /**
     * 设置仓库高 / 最大层数，FR-1.1。
     *
     * @param height 仓库高 / 最大层数，FR-1.1
     */
    public void setHeight(Integer height) {
        this.height = height;
    }

    /**
     * 获取出库口平面坐标 x，距离计算终点。
     *
     * @return 出库口平面坐标 x，距离计算终点
     */
    public Integer getExitX() {
        return exitX;
    }

    /**
     * 设置出库口平面坐标 x，距离计算终点。
     *
     * @param exitX 出库口平面坐标 x，距离计算终点
     */
    public void setExitX(Integer exitX) {
        this.exitX = exitX;
    }

    /**
     * 获取出库口平面坐标 y，距离计算终点。
     *
     * @return 出库口平面坐标 y，距离计算终点
     */
    public Integer getExitY() {
        return exitY;
    }

    /**
     * 设置出库口平面坐标 y，距离计算终点。
     *
     * @param exitY 出库口平面坐标 y，距离计算终点
     */
    public void setExitY(Integer exitY) {
        this.exitY = exitY;
    }

    /**
     * 获取出库口层号，默认 1（预留给三维扩展）。
     *
     * @return 出库口层号，默认 1（预留给三维扩展）
     */
    public Integer getExitLayer() {
        return exitLayer;
    }

    /**
     * 设置出库口层号，默认 1（预留给三维扩展）。
     *
     * @param exitLayer 出库口层号，默认 1（预留给三维扩展）
     */
    public void setExitLayer(Integer exitLayer) {
        this.exitLayer = exitLayer;
    }

    /**
     * 获取备注。
     *
     * @return 备注
     */
    public String getRemark() {
        return remark;
    }

    /**
     * 设置备注。
     *
     * @param remark 备注
     */
    public void setRemark(String remark) {
        this.remark = remark;
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
