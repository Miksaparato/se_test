package com.wms.recommend.dto;

/** 采用推荐结果，对应 API-043 响应。 */
public record AdoptResult(Long locationId, String code, String status, double score) {
}
