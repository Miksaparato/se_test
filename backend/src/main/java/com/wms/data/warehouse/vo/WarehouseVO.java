package com.wms.data.warehouse.vo;

import com.wms.domain.entity.Warehouse;

import java.time.LocalDateTime;

/**
 * 仓库视图对象（API-016 ~ API-019）。出库口以 {@code exit} 嵌套对象返回，
 * 与《接口文档》API-021 示例一致。
 *
 * @param id        仓库 id
 * @param code      仓库编码
 * @param name      仓库名称
 * @param length    仓库长
 * @param width     仓库宽
 * @param height    仓库高 / 最大层数
 * @param exit      出库口坐标
 * @param rackCount 货架数量
 * @param remark    备注
 * @param createdAt 创建时间(UTC)
 * @author a
 */
public record WarehouseVO(
        Long id,
        String code,
        String name,
        Integer length,
        Integer width,
        Integer height,
        ExitPoint exit,
        long rackCount,
        String remark,
        LocalDateTime createdAt) {

    /**
     * 出库口坐标（曼哈顿距离终点，COM-6）。
     *
     * @param x     平面坐标 x
     * @param y     平面坐标 y
     * @param layer 层号
     */
    public record ExitPoint(Integer x, Integer y, Integer layer) {
    }

    /**
     * 由实体构造。
     *
     * @param warehouse 仓库实体
     * @param rackCount 货架数量
     * @return 仓库视图对象
     */
    public static WarehouseVO from(Warehouse warehouse, long rackCount) {
        return new WarehouseVO(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getLength(),
                warehouse.getWidth(),
                warehouse.getHeight(),
                new ExitPoint(warehouse.getExitX(), warehouse.getExitY(), warehouse.getExitLayer()),
                rackCount,
                warehouse.getRemark(),
                warehouse.getCreatedAt());
    }
}
