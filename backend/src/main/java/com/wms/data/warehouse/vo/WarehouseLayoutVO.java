package com.wms.data.warehouse.vo;

import com.wms.data.location.vo.LocationVO;
import com.wms.data.warehouse.vo.WarehouseVO.ExitPoint;

import java.util.List;

/**
 * 仓库平面布局（API-021），供 c 的平面图公共组件（C-F1）与仓库总览页（C-F2）渲染。
 *
 * <p>结构与《接口文档》API-021 示例对齐：顶层含 {@code warehouseId / name / exit} 与
 * {@code racks[]}，货架下嵌套 {@code locations[]}；{@code code/length/width/height} 为附加信息。
 *
 * @param warehouseId 仓库 id
 * @param code        仓库编码
 * @param name        仓库名称
 * @param length      仓库长
 * @param width       仓库宽
 * @param height      仓库高 / 最大层数
 * @param exit        出库口坐标
 * @param racks       货架及其库位
 * @author a
 */
public record WarehouseLayoutVO(
        Long warehouseId,
        String code,
        String name,
        Integer length,
        Integer width,
        Integer height,
        ExitPoint exit,
        List<LayoutRack> racks) {

    /**
     * 布局中的货架节点。
     *
     * @param rackId      货架 id
     * @param code        货架编码
     * @param aisle       巷道
     * @param columnCount 列数
     * @param layerCount  层数
     * @param x           基准坐标 x
     * @param y           基准坐标 y
     * @param orientation 库位排布方向
     * @param locations   库位列表
     */
    public record LayoutRack(
            Long rackId,
            String code,
            String aisle,
            Integer columnCount,
            Integer layerCount,
            Integer x,
            Integer y,
            String orientation,
            List<LocationVO.LayoutItem> locations) {
    }

    /**
     * 构造布局对象。
     *
     * @param warehouseId 仓库 id
     * @param code        仓库编码
     * @param name        仓库名称
     * @param length      仓库长
     * @param width       仓库宽
     * @param height      仓库高
     * @param exit        出库口
     * @param racks       货架列表
     * @return 布局对象
     */
    public static WarehouseLayoutVO of(Long warehouseId, String code, String name, Integer length,
                                       Integer width, Integer height,
                                       ExitPoint exit,
                                       List<LayoutRack> racks) {
        return new WarehouseLayoutVO(warehouseId, code, name, length, width, height, exit, racks);
    }
}
