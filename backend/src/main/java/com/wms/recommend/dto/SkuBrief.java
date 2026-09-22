package com.wms.recommend.dto;

import com.wms.domain.entity.Sku;

/** 推荐结果中的货物摘要，对应 API-041 响应的 sku 字段。 */
public record SkuBrief(Long id, String skuCode, String name, double weight,
                       double turnoverRate, int priority, String category) {

    public static SkuBrief from(Sku s) {
        if (s == null) {
            return null;
        }
        return new SkuBrief(s.getId(), s.getSkuCode(), s.getName(), s.getWeight(),
                s.getTurnoverRate(), s.getPriority(), s.getCategory());
    }
}
