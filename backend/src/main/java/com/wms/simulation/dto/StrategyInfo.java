package com.wms.simulation.dto;

/**
 * 入库策略信息（API-054 响应元素）。
 *
 * @param name        策略标识，API-055 请求的 {@code strategy} 取值
 * @param displayName 中文展示名
 * @param description 策略说明
 * @author c
 */
public record StrategyInfo(String name, String displayName, String description) {
}
