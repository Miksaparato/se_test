package com.wms.domain.entity.type;

/**
 * 方案仿真统计结果（{@code plans.result} JSON 列），结构见《数据库设计说明书》2.6：
 *
 * <pre>
 * {
 *   "totalDistance": 5820,
 *   "avgDistance": 116.4,
 *   "hotAvgDistance": 210.3,
 *   "violations": 5,
 *   "orderCount": 50,
 *   "estimatedTime": 3880
 * }
 * </pre>
 *
 * <p>由 c 的仿真引擎写入、b 的方案对比（API-049/051）读取，字段名与《接口文档》
 * API-051 的对比指标逐一对齐，属**冻结结构**。
 *
 * @param totalDistance  总搬运路程（曼哈顿口径，COM-6）
 * @param avgDistance    平均每单路程
 * @param hotAvgDistance 高频货平均距离
 * @param violations     重货层位违规数
 * @param orderCount     参与出库仿真的订单数
 * @param estimatedTime  出库耗时估算（秒）
 * @author c
 */
public record PlanResult(
        double totalDistance,
        double avgDistance,
        double hotAvgDistance,
        int violations,
        int orderCount,
        double estimatedTime) {

    /** 空结果：尚未做入库/出库仿真的方案使用全 0 快照，避免 JSON 列写入 null 后各处判空。 */
    public static final PlanResult EMPTY = new PlanResult(0.0, 0.0, 0.0, 0, 0, 0.0);
}
