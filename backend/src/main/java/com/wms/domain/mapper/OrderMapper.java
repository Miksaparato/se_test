package com.wms.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 出库订单 Mapper。
 *
 * @author a
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 批量插入订单（SQL 见 {@code resources/mapper/OrderMapper.xml}）。
     *
     * <p>供 c 的「随机测试订单集生成」（API-062 / C-B8）使用：一次生成几十上百条订单时，
     * 逐条 {@code insert} 会产生 N 次数据库往返（《代码规范》4.4「批量写用 saveBatch」）。
     *
     * @param orders 订单实体列表，插入后主键回填到实体的 id 字段
     * @return 影响行数
     */
    int insertBatch(@Param("orders") List<Order> orders);
}
