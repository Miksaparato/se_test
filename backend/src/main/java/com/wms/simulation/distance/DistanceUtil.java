package com.wms.simulation.distance;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.domain.entity.Location;

/**
 * 距离计算统一入口（C-B5 / COM-6）。
 *
 * <p><b>【冻结口径】</b>整个系统的距离都必须走本类，禁止各处自行实现
 * （《代码规范》4.5「距离计算统一走 c 提供的 DistanceUtil.manhattan(a, b)」）：
 * <ul>
 *   <li>推荐引擎的「就近度」{@code ScoringContext.proximity}；</li>
 *   <li>入库策略的「就近分配」；</li>
 *   <li>出库仿真的拣选路程统计（FR-4.2）。</li>
 * </ul>
 *
 * <p>默认口径为**曼哈顿距离**：{@code d = |x1-x2| + |y1-y2|}（《需求文档》4.2）。
 * 另提供欧氏与三维折线两种可选用口径，见 {@link DistanceMetric}。
 *
 * @author c
 */
public final class DistanceUtil {

    /** 层间高度（格），仅三维折线口径使用：一层按 1 格垂直行程折算。 */
    public static final double DEFAULT_LAYER_HEIGHT = 1.0;

    private DistanceUtil() {
    }

    /**
     * 曼哈顿距离（默认口径）。
     *
     * <p>公式：{@code d = |x1 - x2| + |y1 - y2|}。
     *
     * @param from 起点
     * @param to   终点
     * @return 曼哈顿距离
     */
    public static double manhattan(Point from, Point to) {
        return Math.abs((double) from.x() - to.x()) + Math.abs((double) from.y() - to.y());
    }

    /**
     * 曼哈顿距离（按坐标直传，避免为热路径反复装箱）。
     *
     * @param x1 起点 x
     * @param y1 起点 y
     * @param x2 终点 x
     * @param y2 终点 y
     * @return 曼哈顿距离
     */
    public static double manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs((double) x1 - x2) + Math.abs((double) y1 - y2);
    }

    /**
     * 欧氏距离：{@code sqrt((x1-x2)^2 + (y1-y2)^2)}。
     *
     * @param from 起点
     * @param to   终点
     * @return 欧氏距离
     */
    public static double euclidean(Point from, Point to) {
        double dx = (double) from.x() - to.x();
        double dy = (double) from.y() - to.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 三维折线距离：平面曼哈顿 + 层差 × 层高。
     *
     * <p>公式：{@code d = |x1-x2| + |y1-y2| + |layer1 - layer2| × layerHeight}。
     *
     * @param from        起点（平面坐标）
     * @param fromLayer   起点层号
     * @param to          终点（平面坐标）
     * @param toLayer     终点层号
     * @param layerHeight 单层折算高度，非法值时取 {@link #DEFAULT_LAYER_HEIGHT}
     * @return 三维折线距离
     */
    public static double polyline(Point from, int fromLayer, Point to, int toLayer, double layerHeight) {
        double height = layerHeight > 0 ? layerHeight : DEFAULT_LAYER_HEIGHT;
        return manhattan(from, to) + Math.abs((double) fromLayer - toLayer) * height;
    }

    /**
     * 按指定口径计算两个库位之间的搬运距离。
     *
     * @param metric      距离口径
     * @param from        起点库位
     * @param to          终点库位
     * @param layerHeight 单层折算高度（仅 {@link DistanceMetric#POLYLINE} 使用）
     * @return 搬运距离
     */
    public static double between(DistanceMetric metric, Location from, Location to, double layerHeight) {
        if (from == null || to == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "距离计算的库位不能为空");
        }
        return between(metric, pointOf(from), layerOf(from), pointOf(to), layerOf(to), layerHeight);
    }

    /**
     * 按指定口径计算两点之间的搬运距离。
     *
     * @param metric      距离口径
     * @param from        起点
     * @param fromLayer   起点层号
     * @param to          终点
     * @param toLayer     终点层号
     * @param layerHeight 单层折算高度（仅 {@link DistanceMetric#POLYLINE} 使用）
     * @return 搬运距离
     */
    public static double between(DistanceMetric metric, Point from, int fromLayer,
                                 Point to, int toLayer, double layerHeight) {
        DistanceMetric effective = metric == null ? DistanceMetric.MANHATTAN : metric;
        return switch (effective) {
            case EUCLIDEAN -> euclidean(from, to);
            case POLYLINE -> polyline(from, fromLayer, to, toLayer, layerHeight);
            case MANHATTAN -> manhattan(from, to);
        };
    }

    /**
     * 取库位的平面坐标（坐标缺失按 0 处理，避免空指针污染算法）。
     *
     * @param location 库位
     * @return 平面坐标
     */
    public static Point pointOf(Location location) {
        int x = location.getX() == null ? 0 : location.getX();
        int y = location.getY() == null ? 0 : location.getY();
        return new Point(x, y);
    }

    /**
     * 取库位层号（缺失按 1 处理）。
     *
     * @param location 库位
     * @return 层号
     */
    public static int layerOf(Location location) {
        return location.getLayer() == null ? 1 : location.getLayer();
    }
}
