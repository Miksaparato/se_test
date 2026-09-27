package com.wms.simulation.strategy;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.simulation.SimulationContext;

import java.util.List;

/**
 * 入库策略接口（C-B1，插件式设计，对应 NFR-3「策略与评分因子易于新增」）。
 *
 * <p>实现约定（《代码规范》4.5）：
 * <ul>
 *   <li>实现类必须是无状态 Spring Bean，构造注入依赖，便于单元测试；</li>
 *   <li>新增策略只需实现本接口并加 {@code @Component}，{@link StrategyRegistry} 自动注册，
 *       <b>不需要改动仿真引擎或 Controller</b>；</li>
 *   <li>返回的库位必须来自入参 {@code candidates}，不得自行查库；</li>
 *   <li>不得修改候选库位的状态——占用状态由 {@link SimulationContext} 统一维护。</li>
 * </ul>
 *
 * @author c
 */
public interface InboundStrategy {

    /**
     * 策略标识，与《接口文档》API-055 的 {@code strategy} 取值一致。
     *
     * @return 策略标识：random / nearest / zoning / grading / smart / fifo
     */
    String name();

    /**
     * 策略中文展示名，如「智能推荐」。
     *
     * @return 中文展示名
     */
    String displayName();

    /**
     * 策略说明，供 API-054 策略列表与前端下拉框展示。
     *
     * @return 一句话说明
     */
    String description();

    /**
     * 为一次入库挑选库位。
     *
     * @param sku        待入库货物
     * @param quantity   本次入库数量（部分策略会参考数量，如分区存储按频次分区）
     * @param candidates 当前可用候选库位（已通过硬约束过滤，且未被本次仿真选走）
     * @param ctx        仿真上下文（出库口坐标、分层规则、分区映射、距离口径）
     * @return 选中的库位与理由；候选为空时应抛业务异常而非返回 null
     */
    StrategyPick pick(Sku sku, int quantity, List<Location> candidates, SimulationContext ctx);
}
