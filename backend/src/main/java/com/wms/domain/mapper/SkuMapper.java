package com.wms.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.domain.entity.Sku;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 货物 Mapper。
 *
 * @author a
 */
@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

    /**
     * 查询 SKU 编码 → id 映射（供订单校验 skuId 是否存在，避免逐条查询）。
     *
     * @param skuIds SKU id 列表
     * @return id 列表（不存在或入参为空时为空列表）
     */
    default List<Long> selectExistingIds(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        return selectBatchIds(skuIds).stream().map(Sku::getId).toList();
    }
}
