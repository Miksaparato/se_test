package com.wms.recommend.compare.service;

import com.wms.recommend.compare.dto.PlanMetric;

import java.util.Comparator;
import java.util.List;

/**
 * 优化建议生成器（B-B6 / FR-5.2）：基于对比结果自动生成可读文字建议。
 * 纯函数实现（NFR-4），便于单测。基线取输入顺序中的第一个方案。
 */
public final class SuggestionGenerator {

    private SuggestionGenerator() {
    }

    public static String generate(List<PlanMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return "无可用方案。";
        }
        PlanMetric baseline = metrics.get(0);
        PlanMetric best = metrics.stream()
                .min(Comparator.comparingDouble(PlanMetric::totalDistance))
                .orElse(baseline);

        StringBuilder sb = new StringBuilder();
        if (metrics.size() >= 2) {
            if (best.totalDistance() < baseline.totalDistance() - 1e-9) {
                double pct = (baseline.totalDistance() - best.totalDistance()) / baseline.totalDistance() * 100;
                sb.append(String.format("方案「%s」（%s）相比「%s」总搬运路程下降 %.1f%%，建议采用「%s」。",
                        best.name(), best.strategy(), baseline.name(), pct, best.name()));
            } else {
                sb.append(String.format("方案「%s」（%s）总搬运路程最短，可保持当前方案。",
                        best.name(), best.strategy()));
            }
        } else {
            sb.append(String.format("仅有一个方案「%s」（%s），可作为基准方案。",
                    best.name(), best.strategy()));
        }

        PlanMetric hotBest = metrics.stream()
                .min(Comparator.comparingDouble(PlanMetric::hotAvgDistance))
                .orElse(best);
        if (metrics.size() >= 2 && hotBest.hotAvgDistance() < baseline.hotAvgDistance() - 1e-9) {
            sb.append(String.format(" 高频货物平均距离最短的是「%s」（%.1f），利于高频出库。",
                    hotBest.name(), hotBest.hotAvgDistance()));
        }

        for (PlanMetric m : metrics) {
            if (m.violations() > 0) {
                sb.append(String.format(" 方案「%s」存在 %d 处重货层高违规，建议调整分层规则或改用智能推荐策略。",
                        m.name(), m.violations()));
            }
        }
        return sb.toString();
    }
}
