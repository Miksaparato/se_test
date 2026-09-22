package com.wms.recommend.compare.dto;

import java.util.List;

/** 方案对比结果，对应 API-051 响应。 */
public record CompareResult(List<PlanMetric> metrics, String suggestion) {
}
