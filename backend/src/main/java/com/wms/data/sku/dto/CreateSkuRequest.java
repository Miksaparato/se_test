package com.wms.data.sku.dto;

import com.wms.domain.entity.type.SkuSize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建货物请求体（API-030），字段与《接口文档》示例一致：
 *
 * <pre>
 * {
 *   "skuCode": "SKU-001", "name": "高频电子元件", "weight": 50,
 *   "turnoverRate": 0.9, "priority": 5, "category": "电子",
 *   "size": { "length": 30, "width": 20, "height": 10 }
 * }
 * </pre>
 *
 * @param skuCode      SKU 编码，全局唯一
 * @param name         货物名称
 * @param weight       重量(kg)，推荐评分 S_weight 输入
 * @param turnoverRate 周转频次，推荐评分 S_freq 输入
 * @param priority     出库优先级 1~5，推荐评分 S_priority 输入
 * @param category     品类
 * @param size         尺寸（长宽高）
 * @param remark       备注
 * @author a
 */
public record CreateSkuRequest(
        @NotBlank(message = "SKU 编码不能为空")
        @Size(max = 32, message = "SKU 编码长度不能超过 32 个字符")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "SKU 编码只能包含字母、数字、下划线、连字符")
        String skuCode,

        @NotBlank(message = "货物名称不能为空")
        @Size(max = 64, message = "货物名称长度不能超过 64 个字符")
        String name,

        @NotNull(message = "重量不能为空")
        @DecimalMin(value = "0", message = "重量不能为负数")
        BigDecimal weight,

        @NotNull(message = "周转频次不能为空")
        @DecimalMin(value = "0", message = "周转频次不能为负数")
        BigDecimal turnoverRate,

        @NotNull(message = "出库优先级不能为空")
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
