package com.wms.domain.entity;

/**
 * 库位分配方案——对应《接口文档》API-055 / API-049 冻结字段（c 写、b 读）。
 * 方案数据由成员 c 的仿真引擎产出，本模块（b）消费其统计指标做对比分析。
 */
public class Plan {

    private Long id;
    private String name;
    /** 策略名，如「随机分配」「就近分配」「智能推荐」。 */
    private String strategy;
    /** 全部订单总搬运路程。 */
    private double totalDistance;
    /** 平均每单路程。 */
    private double avgDistance;
    /** 高频货物平均距离。 */
    private double hotAvgDistance;
    /** 重货层高违规数。 */
    private int violations;

    public Plan() {
    }

    public Plan(Long id, String name, String strategy, double totalDistance,
                double avgDistance, double hotAvgDistance, int violations) {
        this.id = id;
        this.name = name;
        this.strategy = strategy;
        this.totalDistance = totalDistance;
        this.avgDistance = avgDistance;
        this.hotAvgDistance = hotAvgDistance;
        this.violations = violations;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }

    public double getAvgDistance() {
        return avgDistance;
    }

    public void setAvgDistance(double avgDistance) {
        this.avgDistance = avgDistance;
    }

    public double getHotAvgDistance() {
        return hotAvgDistance;
    }

    public void setHotAvgDistance(double hotAvgDistance) {
        this.hotAvgDistance = hotAvgDistance;
    }

    public int getViolations() {
        return violations;
    }

    public void setViolations(int violations) {
        this.violations = violations;
    }
}
