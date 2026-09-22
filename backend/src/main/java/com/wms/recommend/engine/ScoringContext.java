package com.wms.recommend.engine;

import com.wms.domain.entity.Location;
import com.wms.domain.entity.Warehouse;

import java.util.List;
import java.util.Map;

/**
 * 评分上下文：缓存一次推荐计算中共享的量（出库口坐标、最大层号、min/max 距离），避免重复计算。
 * 由 {@link ScoringEngine} 构建并注入各分项打分器。
 */
public class ScoringContext {

    private final int exitX;
    private final int exitY;
    private final int maxLayer;
    private final double minDistance;
    private final double maxDistance;
    private final StorageRuleConfig rules;
    private final Map<String, Boolean> occupied;

    public ScoringContext(Warehouse warehouse, List<Location> candidates,
                          StorageRuleConfig rules, Map<String, Boolean> occupied) {
        this.exitX = warehouse.getExitX();
        this.exitY = warehouse.getExitY();
        this.rules = rules;
        this.occupied = occupied;

        int ml = 1;
        double dmin = Double.MAX_VALUE;
        double dmax = Double.MIN_VALUE;
        for (Location l : candidates) {
            ml = Math.max(ml, l.getLayer());
            double d = distance(l.getX(), l.getY());
            dmin = Math.min(dmin, d);
            dmax = Math.max(dmax, d);
        }
        this.maxLayer = ml;
        this.minDistance = candidates.isEmpty() ? 0 : dmin;
        this.maxDistance = candidates.isEmpty() ? 0 : dmax;
    }

    /** 曼哈顿距离（COM-6 约定口径）：|x1-x2| + |y1-y2|。 */
    public double distance(int x, int y) {
        return Math.abs(x - exitX) + Math.abs(y - exitY);
    }

    /** 就近度：归一化到 [0,1]，1 表示离出库口最近。 */
    public double proximity(int x, int y) {
        double d = distance(x, y);
        if (maxDistance <= minDistance) {
            return 1.0;
        }
        return (maxDistance - d) / (maxDistance - minDistance);
    }

    /** 层适配度：归一化到 (0,1]，层号 1（最底层）得 1，层越高分越低。 */
    public double layerSuitability(int layer) {
        if (maxLayer <= 1) {
            return 1.0;
        }
        return (maxLayer - layer + 1) / (double) maxLayer;
    }

    /** 库位是否位于黄金区（距出库口在黄金区半径内）。 */
    public boolean inGoldenZone(int x, int y) {
        return distance(x, y) <= rules.getGoldenZoneRadius();
    }

    /** 空位连续性：正交四邻（同层）中被占用的比例，取值 [0,1]。 */
    public double continuity(Location l) {
        int occupiedNeighbors = 0;
        int[][] deltas = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : deltas) {
            if (Boolean.TRUE.equals(occupied.get(
                    CoordKey.key(l.getX() + d[0], l.getY() + d[1], l.getLayer())))) {
                occupiedNeighbors++;
            }
        }
        return occupiedNeighbors / 4.0;
    }

    public int getExitX() {
        return exitX;
    }

    public int getExitY() {
        return exitY;
    }

    public int getMaxLayer() {
        return maxLayer;
    }

    public double getMinDistance() {
        return minDistance;
    }

    public double getMaxDistance() {
        return maxDistance;
    }

    public StorageRuleConfig getRules() {
        return rules;
    }
}
