package com.wms.simulation;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.recommend.engine.StorageRuleConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 约束校验模块（C-B7，对应《需求文档》4.4 三条约束与《数据库设计说明书》5.2）。
 *
 * <p>三条约束按「硬约束 / 软约束」分开处理，这是本模块的关键口径：
 *
 * <table border="1">
 *   <caption>约束口径</caption>
 *   <tr><th>约束</th><th>类型</th><th>处理</th></tr>
 *   <tr>
 *     <td>一个库位同一时刻最多一种货物</td><td>硬</td>
 *     <td>候选集只保留空闲库位，且仿真过程中被选走的库位立即移出候选</td>
 *   </tr>
 *   <tr>
 *     <td>货物尺寸不得超出库位容量</td><td>硬</td>
 *     <td>体积 = 长×宽×高 &gt; {@code locations.capacity} 直接排除</td>
 *   </tr>
 *   <tr>
 *     <td>重货禁止分配到超过预设层高</td><td><b>软</b></td>
 *     <td>不排除候选，而是「判定 + 计数」：智能推荐/分级存储会主动避开，
 *         随机/就近/分区/FIFO 可能命中，命中的记为该方案的
 *         {@code violations}（FR-5.1 对比指标之一）</td>
 *   </tr>
 * </table>
 *
 * <p>为什么重货层高要做成软约束：若所有策略都强制过滤，方案对比里的「重货层位违规数」
 * 将恒为 0，该指标就失去意义（《接口文档》API-051 示例中「随机分配」违规 5 处、
 * 「就近分配/智能推荐」为 0，正是这个语义）。
 *
 * @author c
 */
public final class ConstraintChecker {

    private ConstraintChecker() {
    }

    /**
     * 按硬约束筛选候选库位：必须空闲（且未被本次仿真选走），且容量放得下货物。
     *
     * @param sku    待入库货物
     * @param source 原始库位集合
     * @param ctx    仿真上下文（提供「仿真期间是否已被选走」）
     * @return 可用的候选库位
     */
    public static List<Location> hardFilter(Sku sku, List<Location> source, SimulationContext ctx) {
        List<Location> eligible = new ArrayList<>();
        for (Location location : source) {
            if (!ctx.isAvailable(location)) {
                continue;
            }
            if (!fitsCapacity(sku, location)) {
                continue;
            }
            eligible.add(location);
        }
        return eligible;
    }

    /**
     * 容量校验：SKU 体积（长×宽×高）不得超过库位容量。
     *
     * <p>与 b 的推荐引擎共用同一判定（{@link com.wms.domain.entity.type.SkuSize#fitsWithin}），
     * 保证「推荐得出来的库位，仿真也一定判为可用」。未录入尺寸时视为无容量约束
     * （《数据库设计说明书》5.2：「若录入尺寸，则校验」）。
     *
     * @param sku      货物
     * @param location 库位
     * @return 放得下返回 true
     */
    public static boolean fitsCapacity(Sku sku, Location location) {
        if (sku.getSize() == null) {
            return true;
        }
        return sku.getSize().fitsWithin(location.getCapacity());
    }

    /**
     * 重货层高判定（软约束）：货物重量达到阈值时，只允许存放在
     * {@code layer <= maxLayerForHeavy} 的库位。
     *
     * @param rules    分层规则（阈值来自 b 的 API-046 配置，可调）
     * @param sku      货物
     * @param location 库位
     * @return 违规返回 true；非重货或规则未启用时返回 false
     */
    public static boolean violatesHeavyLayer(StorageRuleConfig rules, Sku sku, Location location) {
        if (rules == null || sku == null || location == null) {
            return false;
        }
        double weight = sku.weightValue();
        int layer = location.getLayer() == null ? 1 : location.getLayer();
        return !rules.allowsHeavyOnLayer(weight, layer);
    }
}
