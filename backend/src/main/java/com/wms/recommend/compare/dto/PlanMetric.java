package com.wms.recommend.compare.dto;

import com.wms.domain.entity.Plan;

/** 方案指标，对应 API-051 响应的 metrics 元素（FR-5.1 对比指标）。 */
public record PlanMetric(Long planId, String name, String strategy,
                         double totalDistance, double avgDistance,
                         double hotAvgDistance, int violations) {

    public static PlanMetric from(Plan p) {
        return new PlanMetric(p.getId(), p.getName(), p.getStrategy(),
                p.getTotalDistance(), p.getAvgDistance(),
                p.getHotAvgDistance(), p.getViolations());
    }
}
