package com.wms.data.warehouse.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 更新仓库请求体（API-019）。
 *
 * <p>仓库编码 {@code code} 是库位编码与平面图数据的引用基础，**不提供修改**。
 * 为空字段表示保持不变（MyBatis-Plus 默认忽略 null 字段）。
 *
 * @param name      仓库名称
 * @param length    仓库长
 * @param width     仓库宽
 * @param height    仓库高 / 最大层数
 * @param exitX     出库口坐标 x
 * @param exitY     出库口坐标 y
 * @param exitLayer 出库口层号
 * @param remark    备注
 * @author a
 */
public record UpdateWarehouseRequest(
        @Size(max = 64, message = "仓库名称长度不能超过 64 个字符")
        String name,

        @Min(value = 0, message = "仓库长不能为负数")
        Integer length,

        @Min(value = 0, message = "仓库宽不能为负数")
        Integer width,

        @Min(value = 0, message = "仓库高不能为负数")
        Integer height,

        @Min(value = 0, message = "出库口坐标 x 不能为负数")
        Integer exitX,

        @Min(value = 0, message = "出库口坐标 y 不能为负数")
        Integer exitY,

        @Min(value = 1, message = "出库口层号最小为 1")
        Integer exitLayer,

        @Size(max = 255, message = "备注长度不能超过 255 个字符")
        String remark) {
}
