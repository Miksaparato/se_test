package com.wms.simulation.order.dto;

import java.util.List;

/**
 * 随机测试订单集生成结果（API-062 响应）。
 *
 * @param generated 实际生成条数
 * @param orderIds  生成的订单 id 列表（可直接喂给 API-058 出库仿真）
 * @param firstOrderNo 首个订单号，便于在订单列表里定位这批数据
 * @param randomSeed 本次使用的随机种子（记下来即可复现同一批订单）
 * @author c
 */
public record GenerateOrdersResult(
        int generated,
        List<Long> orderIds,
        String firstOrderNo,
        long randomSeed) {
}
