package com.wms.recommend.engine;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评分引擎（核心，B-B1 / B-B2）：对空闲库位计算综合评分并降序排序。
 *
 * <p>综合得分（算法 5.1）：
 * <pre>Score(L) = W_w·S_weight + W_f·S_freq + W_p·S_priority + W_o·S_other</pre>
 * 各分项归一化到 [0,1]，权重之和为 1；评分前先按硬约束（重货层高，算法 5.4）过滤候选库位。</p>
 *
 * <p>本类为无状态服务（NFR-4），不依赖 Spring 上下文副作用，便于纯 JUnit 单测。</p>
 */
@Component
public class ScoringEngine {

    private final List<Scorer> scorers;

    public ScoringEngine(List<Scorer> scorers) {
        this.scorers = scorers;
    }

    /**
     * 对空闲库位评分并降序排序。
     *
     * @param sku           待入库货物
     * @param freeLocations 空闲库位
     * @param warehouse     仓库（含出库口坐标）
     * @param weights       评分权重（之和须为 1）
     * @param rules         分层规则
     * @param occupied      全仓库占用快照（key = "x,y,layer"），供空位连续性计算
     * @return 按总分降序的评分结果
     */
    public List<LocationScore> score(Sku sku, List<Location> freeLocations,
                                     Warehouse warehouse, ScoreWeightConfig weights,
                                     StorageRuleConfig rules, Map<String, Boolean> occupied) {
        weights.validate();
        rules.validate();

        // 1. 硬约束过滤（《需求文档》4.4）：
        //    a) 重货禁止分配到超过预设层高（算法 5.4）；
        //    b) 货物尺寸不得超出库位容量——与 c 的约束校验模块共用 SkuSize.fitsWithin，
        //       否则会出现「推荐得出来、仿真判为不可用」的口径分裂。
        List<Location> eligible = freeLocations.stream()
                .filter(l -> rules.allowsHeavyOnLayer(sku.weightValue(), l.getLayer()))
                .filter(l -> !capacityExceeded(sku, l))
                .toList();

        // 2. 构建评分上下文（计算 min/max 距离、最大层号等共享量）
        ScoringContext ctx = new ScoringContext(warehouse, eligible, rules, occupied);

        // 3. 逐库位计算加权总分
        List<LocationScore> result = new ArrayList<>(eligible.size());
        for (Location loc : eligible) {
            Map<String, Double> raw = new LinkedHashMap<>();
            Map<String, Double> subScores = new LinkedHashMap<>();
            double total = 0.0;
            for (Scorer scorer : scorers) {
                double s = scorer.score(sku, loc, ctx);
                raw.put(scorer.key(), s);
                double weighted = weightOf(weights, scorer.key()) * s;
                subScores.put(scorer.key(), round(weighted));
                total += weighted;
            }
            total = round(total);
            result.add(new LocationScore(loc.getId(), loc.getCode(), total,
                    subScores, ReasonBuilder.build(sku, loc, ctx, raw)));
        }

        // 4. 降序排序
        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return result;
    }

    private double weightOf(ScoreWeightConfig weights, String key) {
        return switch (key) {
            case "weight" -> weights.getWeight();
            case "freq" -> weights.getFreq();
            case "priority" -> weights.getPriority();
            case "other" -> weights.getOther();
            default -> 0.0;
        };
    }

    /**
     * 容量硬约束：货物体积超过库位容量时该库位不可用。
     *
     * @param sku      待入库货物
     * @param location 候选库位
     * @return 超出容量返回 true
     */
    private static boolean capacityExceeded(Sku sku, Location location) {
        return sku.getSize() != null && !sku.getSize().fitsWithin(location.getCapacity());
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
