package com.wms.data.rack.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新货架请求体（API-023）。
 *
 * <p>货架编码与巷道**不提供修改**：它们是已生成库位编码（如 {@code A-01-03-02}）的组成部分，
 * 一旦改动会导致库位编码与货架不一致。
 *
 * @param columnCount 列数
 * @param layerCount  层数
 * @param x           货架基准平面坐标 x
 * @param y           货架基准平面坐标 y
 * @param orientation 库位排布方向
 * @author a
 */
public record UpdateRackRequest(
        @Min(value = 1, message = "列数最小为 1")
        Integer columnCount,

        @Min(value = 1, message = "层数最小为 1")
        Integer layerCount,

        @Min(value = 0, message = "坐标 x 不能为负数")
        Integer x,

        @Min(value = 0, message = "坐标 y 不能为负数")
        Integer y,

        @Pattern(regexp = "^(row|column)$", message = "排布方向只能是 row 或 column")
        @Size(max = 16, message = "排布方向长度不能超过 16 个字符")
        String orientation) {
}
