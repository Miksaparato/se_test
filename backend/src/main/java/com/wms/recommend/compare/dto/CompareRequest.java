package com.wms.recommend.compare.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 多方案对比请求，对应 API-051 请求体。 */
public record CompareRequest(@NotEmpty(message = "planIds 不能为空") List<Long> planIds) {
}
