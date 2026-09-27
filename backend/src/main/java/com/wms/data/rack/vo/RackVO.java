package com.wms.data.rack.vo;

import com.wms.domain.entity.Rack;

import java.time.LocalDateTime;

/**
 * 货架视图对象（API-022 ~ API-023）。
 *
 * @param id            货架 id
 * @param warehouseId   所属仓库 id
 * @param code          货架编码
 * @param aisle         巷道
 * @param columnCount   列数
 * @param layerCount    层数
 * @param x             基准平面坐标 x
 * @param y             基准平面坐标 y
 * @param orientation   库位排布方向
 * @param locationCount 已生成库位数量
 * @param createdAt     创建时间(UTC)
 * @author a
 */
public record RackVO(
        Long id,
        Long warehouseId,
        String code,
        String aisle,
        Integer columnCount,
        Integer layerCount,
        Integer x,
        Integer y,
        String orientation,
        long locationCount,
        LocalDateTime createdAt) {

    /**
     * 由实体构造。
     *
     * @param rack          货架实体
     * @param locationCount 已生成库位数量
     * @return 货架视图对象
     */
    public static RackVO from(Rack rack, long locationCount) {
        return new RackVO(
                rack.getId(),
                rack.getWarehouseId(),
                rack.getCode(),
                rack.getAisle(),
                rack.getColumnCount(),
                rack.getLayerCount(),
                rack.getX(),
                rack.getY(),
                rack.getOrientation(),
                locationCount,
                rack.getCreatedAt());
    }
}
