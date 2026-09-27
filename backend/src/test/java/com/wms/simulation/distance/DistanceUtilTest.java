package com.wms.simulation.distance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 距离计算单元测试（T-2 / C-B5 / COM-6）。
 *
 * <p>用例覆盖《代码规范》4.7 给出的示例（曼哈顿距离 6.0），以及欧氏、三维折线两种可选口径
 * 与非法入参的边界行为。
 *
 * @author c
 */
class DistanceUtilTest {

    @Test
    @DisplayName("曼哈顿距离：|x1-x2|+|y1-y2|（《代码规范》4.7 示例）")
    void should_compute_manhattan_distance() {
        assertEquals(6.0, DistanceUtil.manhattan(new Point(3, 2), new Point(0, 5)));
    }

    @Test
    @DisplayName("曼哈顿距离：支持按坐标直传，且对负数坐标取绝对值")
    void should_compute_manhattan_from_coordinates() {
        assertEquals(6.0, DistanceUtil.manhattan(3, 2, 0, 5));
        assertEquals(7.0, DistanceUtil.manhattan(-3, -2, 0, 2));
    }

    @Test
    @DisplayName("欧氏距离：3-4-5 直角三角形应为 5")
    void should_compute_euclidean_distance() {
        assertEquals(5.0, DistanceUtil.euclidean(new Point(0, 0), new Point(3, 4)), 1e-9);
    }

    @Test
    @DisplayName("三维折线距离：平面曼哈顿 + 层差×层高")
    void should_compute_polyline_distance() {
        // 平面 |3-0| + |2-5| = 6；层差 |1-3| = 2，层高 1.5 → 6 + 3 = 9
        assertEquals(9.0, DistanceUtil.polyline(new Point(3, 2), 1, new Point(0, 5), 3, 1.5), 1e-9);
    }

    @Test
    @DisplayName("三维折线距离：层高非法时回落到默认层高 1.0，不应抛异常")
    void should_fallback_to_default_layer_height() {
        assertEquals(8.0, DistanceUtil.polyline(new Point(3, 2), 1, new Point(0, 5), 3, 0), 1e-9);
    }

    @Test
    @DisplayName("口径解析：无法识别的标识回落到默认曼哈顿，不抛异常")
    void should_parse_metric_with_default() {
        assertEquals(DistanceMetric.MANHATTAN, DistanceMetric.parse(null));
        assertEquals(DistanceMetric.MANHATTAN, DistanceMetric.parse("  "));
        assertEquals(DistanceMetric.MANHATTAN, DistanceMetric.parse("unknown"));
        assertEquals(DistanceMetric.EUCLIDEAN, DistanceMetric.parse("EUCLIDEAN"));
        assertEquals(DistanceMetric.POLYLINE, DistanceMetric.parse(" polyline "));
    }

    @Test
    @DisplayName("同一口径下 between 与显式方法结果一致")
    void should_agree_between_and_explicit_methods() {
        Point a = new Point(2, 2);
        Point b = new Point(7, 9);
        assertTrue(Math.abs(DistanceUtil.between(DistanceMetric.MANHATTAN, a, 1, b, 1, 1.0)
                - DistanceUtil.manhattan(a, b)) < 1e-9);
        assertTrue(Math.abs(DistanceUtil.between(DistanceMetric.EUCLIDEAN, a, 1, b, 1, 1.0)
                - DistanceUtil.euclidean(a, b)) < 1e-9);
        assertTrue(Math.abs(DistanceUtil.between(DistanceMetric.POLYLINE, a, 1, b, 4, 1.0)
                - DistanceUtil.polyline(a, 1, b, 4, 1.0)) < 1e-9);
    }
}
