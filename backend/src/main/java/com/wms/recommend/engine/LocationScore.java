package com.wms.recommend.engine;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个库位的评分结果——对应《接口文档》API-041 的 candidates 元素。
 * {@code subScores} 为各分项「加权后」得分，{@code score} 为其总和（与接口文档示例一致）。
 */
public class LocationScore {

    private Long locationId;
    private String code;
    private double score;
    private Map<String, Double> subScores = new LinkedHashMap<>();
    private String reason;

    public LocationScore() {
    }

    public LocationScore(Long locationId, String code, double score,
                         Map<String, Double> subScores, String reason) {
        this.locationId = locationId;
        this.code = code;
        this.score = score;
        this.subScores = subScores;
        this.reason = reason;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public Map<String, Double> getSubScores() {
        return subScores;
    }

    public void setSubScores(Map<String, Double> subScores) {
        this.subScores = subScores;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
