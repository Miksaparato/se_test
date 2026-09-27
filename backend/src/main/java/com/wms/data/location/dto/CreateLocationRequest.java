package com.wms.data.location.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建库位请求体（API-025）。
 *
 * <p>库位编码建议遵循「巷道-货架序号-列-层」，如 {@code A-01-03-02}；
 * 但接口不做强制格式校验，以便支持手工编码（仅要求全局唯一）。
 *
 * @param rackId       所属货架 id
 * @param code         库位唯一编码
 * @param x            平面坐标 x（曼哈顿距离起点）
 * @param y            平面坐标 y
 * @param layer        层号，从 1 开始，越小越靠地面
 * @param status       状态：free / occupied / disabled，缺省 free
 * @param capacity     库位容量（体积口径）
 * @param occupiedSkuId 占用货物 id（仅 status=occupied 时允许）
 * @author a
 */
public record CreateLocationRequest(
        @NotNull(message = "所属货架 id 不能为空")
        Long rackId,

        @NotBlank(message = "库位编码不能为空")
        @Size(max = 32, message = "库位编码长度不能超过 32 个字符")
        String code,

        @NotNull(message = "平面坐标 x 不能为空")
        @Min(value = 0, message = "平面坐标 x 不能为负数")
        Integer x,

        @NotNull(message = "平面坐标 y 不能为空")
        @Min(value = 0, message = "平面坐标 y 不能为负数")
        Integer y,

        @NotNull(message = "层号不能为空")
        @Min(value = 1, message = "层号最小为 1")
        @Max(value = 999, message = "层号最大为 999")
        Integer layer,

        @Pattern(regexp = "^(free|occupied|disabled)$", message = "库位状态只能是 free、occupied 或 disabled")
        String status,

        @DecimalMin(value = "0", message = "库位容量不能为负数")
        BigDecimal capacity,

        Long occupiedSkuId) {
}
