package com.wms.data.location.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * 更新库位请求体（API-026）。
 *
 * <p>库位编码 {@code code} 与所属货架**不提供修改**：编码是推荐/仿真结果的引用键。
 *
 * @param x            平面坐标 x
 * @param y            平面坐标 y
 * @param layer        层号
 * @param status       状态：free / occupied / disabled
 * @param capacity     库位容量
 * @param occupiedSkuId 占用货物 id
 * @author a
 */
public record UpdateLocationRequest(
        @Min(value = 0, message = "平面坐标 x 不能为负数")
        Integer x,

        @Min(value = 0, message = "平面坐标 y 不能为负数")
        Integer y,

        @Min(value = 1, message = "层号最小为 1")
        @Max(value = 999, message = "层号最大为 999")
        Integer layer,

        @Pattern(regexp = "^(free|occupied|disabled)$", message = "库位状态只能是 free、occupied 或 disabled")
        String status,

        @DecimalMin(value = "0", message = "库位容量不能为负数")
        BigDecimal capacity,

        Long occupiedSkuId) {
}
