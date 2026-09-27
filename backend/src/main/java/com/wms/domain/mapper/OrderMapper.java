package com.wms.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 出库订单 Mapper。
 *
 * @author a
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
