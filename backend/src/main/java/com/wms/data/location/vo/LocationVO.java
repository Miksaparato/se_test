package com.wms.data.location.vo;

import com.wms.domain.entity.Location;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库位视图对象（API-025 ~ API-027，以及 API-021 布局中的库位节点）。
 *
 * <p>字段名与《接口文档》API-021 示例逐一对齐（{@code id/code/x/y/layer/status/capacity}），
 * 供 c 的平面图组件（C-F1）直接消费。
 *
 * @param id            库位 id
 * @param rackId        所属货架 id
 * @param warehouseId   所属仓库 id
 * @param code          库位唯一编码
 * @param x             平面坐标 x
 * @param y             平面坐标 y
 * @param layer         层号
 * @param status        状态：free / occupied / disabled
 * @param capacity      库位容量
 * @param occupiedSkuId 占用货物 id
 * @param createdAt     创建时间(UTC)
 * @author a
 */
public record LocationVO(
        Long id,
        Long rackId,
        Long warehouseId,
        String code,
        Integer x,
        Integer y,
        Integer layer,
        String status,
        BigDecimal capacity,
        Long occupiedSkuId,
        LocalDateTime createdAt) {

    /**
     * 由实体构造。
     *
     * @param location 库位实体
     * @return 库位视图对象
     */
    public static LocationVO from(Location location) {
        return new LocationVO(
                location.getId(),
                location.getRackId(),
                location.getWarehouseId(),
                location.getCode(),
                location.getX(),
                location.getY(),
                location.getLayer(),
                location.getStatus(),
                location.getCapacity(),
                location.getOccupiedSkuId(),
                location.getCreatedAt());
    }

    /**
     * 构造布局专用节点：**只包含平面图渲染所需字段**，与《接口文档》API-021 示例完全一致，
     * 避免把创建时间等无关字段推给前端。
     *
     * @param location 库位实体
     * @return 布局节点
     */
    public static LayoutItem toLayoutItem(Location location) {
        return new LayoutItem(location.getId(), location.getCode(), location.getX(),
                location.getY(), location.getLayer(), location.getStatus(), location.getCapacity());
    }

    /**
     * 平面图渲染节点（API-021）。
     *
     * @param id       库位 id
     * @param code     库位编码
     * @param x        平面坐标 x
     * @param y        平面坐标 y
     * @param layer    层号
     * @param status   状态：free / occupied / disabled
     * @param capacity 库位容量
     */
    public record LayoutItem(
            Long id,
            String code,
            Integer x,
            Integer y,
            Integer layer,
            String status,
            BigDecimal capacity) {
    }
}
