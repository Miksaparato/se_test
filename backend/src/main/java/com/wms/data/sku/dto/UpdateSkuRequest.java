package com.wms.data.sku.dto;

import com.wms.domain.entity.type.SkuSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 更新货物请求体（API-031）。
 *
 * <p>SKU 编码不可修改：它是订单、库位占用与仿真结果的引用键。
 *
 * @param name         货物名称
 * @param weight       重量(kg)
 * @param turnoverRate 周转频次
 * @param priority     出库优先级 1~5
 * @param category     品类
 * @param size         尺寸
 * @param remark       备注
 * @author a
 */
public record UpdateSkuRequest(
        @Size(max = 64, message = "货物名称长度不能超过 64 个字符")
        String name,

        @DecimalMin(value = "0", message = "重量不能为负数")
        BigDecimal weight,

        @DecimalMin(value = "0", message = "周转频次不能为负数")
        BigDecimal turnoverRate,

        @Min(value = 1, message = "出库优先级最小为 1")
        @Max(value = 5, message = "出库优先级最大为 5")
        Integer priority,

        @Size(max = 32, message = "品类长度不能超过 32 个字符")
        String category,

        @Valid
        SkuSize size,

        @Size(max = 255, message = "备注长度不能超过 255 个字符")
        String remark) {
}
