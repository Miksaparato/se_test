package com.wms.data.sku.vo;

import com.wms.domain.entity.Sku;
import com.wms.domain.entity.type.SkuSize;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 货物视图对象（API-029 ~ API-031）。
 *
 * <p>字段名与《接口文档》API-030 示例一致（{@code skuCode / turnoverRate / size}）。
 *
 * @param id           货物 id
 * @param skuCode      SKU 编码
 * @param name         货物名称
 * @param weight       重量(kg)
 * @param turnoverRate 周转频次
 * @param priority     出库优先级
 * @param category     品类
 * @param size         尺寸
 * @param volume       体积（长×宽×高），供容量校验使用
 * @param remark       备注
 * @param createdAt    创建时间(UTC)
 * @author a
 */
public record SkuVO(
        Long id,
        String skuCode,
        String name,
        BigDecimal weight,
        BigDecimal turnoverRate,
        Integer priority,
        String category,
        SkuSize size,
        int volume,
        String remark,
        LocalDateTime createdAt) {

    /**
     * 由实体构造。
     *
     * @param sku 货物实体
     * @return 货物视图对象
     */
    public static SkuVO from(Sku sku) {
        SkuSize size = sku.getSize();
        return new SkuVO(
                sku.getId(),
                sku.getCode(),
                sku.getName(),
                sku.getWeight(),
                sku.getTurnoverRate(),
                sku.getPriority(),
                sku.getCategory(),
                size,
                size == null ? 0 : size.volume(),
                sku.getRemark(),
                sku.getCreatedAt());
    }
}
