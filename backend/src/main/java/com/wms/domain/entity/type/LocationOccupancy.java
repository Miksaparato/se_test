package com.wms.domain.entity.type;

/**
 * 单个库位的占用明细（{@code plans.location_map} JSON 列的值），结构见《数据库设计说明书》2.6：
 *
 * <pre>
 * {
 *   "1001": { "skuId": 3, "quantity": 20 },
 *   "1002": { "skuId": 5, "quantity": 10 }
 * }
 * </pre>
 *
 * <p>由 c 的仿真引擎写入（键为库位 id 的字符串形式）、b 的方案对比读取，属**冻结结构**。
 *
 * @param skuId    占用该库位的货物 id
 * @param quantity 该库位上的数量
 * @author c
 */
public record LocationOccupancy(Long skuId, Integer quantity) {
}
