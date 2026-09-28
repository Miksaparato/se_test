package com.wms.simulation.distance;

/**
 * 距离口径（COM-6，由 c 牵头统一）。
 *
 * <p>《需求文档》4.2 约定默认曼哈顿距离，并允许扩展为折线/欧氏距离；
 * 《接口文档》API-058 的 {@code speed} 与 {@code distance} 都基于本口径。
 *
 * @author c
 */
public enum DistanceMetric {

    /**
     * 曼哈顿距离（默认口径）：{@code d = |x1-x2| + |y1-y2|}。
     */
    MANHATTAN("曼哈顿距离"),

    /**
     * 欧氏距离：{@code d = sqrt((x1-x2)^2 + (y1-y2)^2)}，用于对比实验。
     */
    EUCLIDEAN("欧氏距离"),

    /**
     * 三维折线距离：{@code d = |x1-x2| + |y1-y2| + |layer1-layer2| × 层高}。
     *
     * <p>在曼哈顿平面距离上叠加垂直取货行程，用于让「高层库位」的路程更接近实际。
     */
    POLYLINE("三维折线距离");

    private final String displayName;

    DistanceMetric(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取中文展示名。
     *
     * @return 中文展示名
     */
    public String displayName() {
        return displayName;
    }

    /**
     * 宽松解析口径标识（大小写不敏感），无法识别时返回默认的 {@link #MANHATTAN}。
     *
     * @param text 口径标识，如 {@code manhattan}
     * @return 距离口径
     */
    public static DistanceMetric parse(String text) {
        if (text == null || text.isBlank()) {
            return MANHATTAN;
        }
        for (DistanceMetric metric : values()) {
            if (metric.name().equalsIgnoreCase(text.trim())) {
                return metric;
            }
        }
        return MANHATTAN;
    }
}
