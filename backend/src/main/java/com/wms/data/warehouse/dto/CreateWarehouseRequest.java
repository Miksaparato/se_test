package com.wms.data.warehouse.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建仓库请求体（API-016）。
 *
 * @param code      仓库编码，全局唯一
 * @param name      仓库名称
 * @param length    仓库长
 * @param width     仓库宽
 * @param height    仓库高 / 最大层数
 * @param exitX     出库口坐标 x（距离计算终点，缺省 0）
 * @param exitY     出库口坐标 y（缺省 0）
 * @param exitLayer 出库口层号（缺省 1）
 * @param remark    备注
 * @author a
 */
public record CreateWarehouseRequest(
        @NotBlank(message = "仓库编码不能为空")
        @Size(max = 32, message = "仓库编码长度不能超过 32 个字符")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "仓库编码只能包含字母、数字、下划线、连字符")
        String code,

        @NotBlank(message = "仓库名称不能为空")
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
