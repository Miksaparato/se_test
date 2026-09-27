package com.wms.data.rack.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建货架请求体（API-022）。
 *
 * @param warehouseId 所属仓库 id
 * @param code        货架编码，仓库内唯一，如 A-01
 * @param aisle       巷道（A/B/C...），库位编码第 1 段
 * @param columnCount 列数（库位编码第 3 段）
 * @param layerCount  层数（库位编码第 4 段）
 * @param x           货架基准平面坐标 x
 * @param y           货架基准平面坐标 y
 * @param orientation 库位排布方向：row 沿 x 递增 / column 沿 y 递增
 * @param generateLocations 是否按列×层批量生成库位（默认 false）
 * @author a
 */
public record CreateRackRequest(
        @NotNull(message = "所属仓库 id 不能为空")
        Long warehouseId,

        @NotBlank(message = "货架编码不能为空")
        @Size(max = 32, message = "货架编码长度不能超过 32 个字符")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "货架编码只能包含字母、数字、下划线、连字符")
        String code,

        @NotBlank(message = "巷道不能为空")
        @Size(max = 16, message = "巷道长度不能超过 16 个字符")
        String aisle,

        @NotNull(message = "列数不能为空")
        @Min(value = 1, message = "列数最小为 1")
        Integer columnCount,

        @NotNull(message = "层数不能为空")
        @Min(value = 1, message = "层数最小为 1")
        Integer layerCount,

        @Min(value = 0, message = "坐标 x 不能为负数")
        Integer x,

        @Min(value = 0, message = "坐标 y 不能为负数")
        Integer y,

        @Pattern(regexp = "^(row|column)$", message = "排布方向只能是 row 或 column")
        String orientation,

        Boolean generateLocations) {
}
