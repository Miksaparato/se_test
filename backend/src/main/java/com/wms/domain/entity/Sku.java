package com.wms.domain.entity;

/**
 * 货物（SKU）——对应《接口文档》API-030 冻结字段（COM-2，成员 a 牵头定稿）。
 * 其中 weight / turnoverRate / priority 是推荐引擎（b）与仿真引擎（c）的核心输入。
 */
public class Sku {

    private Long id;
    private String skuCode;
    private String name;
    /** 重量（kg），重货倾向低层。 */
    private double weight;
    /** 周转频次，归一化到 [0,1]，越高越频繁。 */
    private double turnoverRate;
    /** 出库优先级，1~5，越高越优先。 */
    private int priority;
    /** 品类，用于「其他」分项中的品类匹配。 */
    private String category;
    /** 尺寸（长/宽/高，cm），本期预留容量校验。 */
    private double length;
    private double width;
    private double height;

    public Sku() {
    }

    public Sku(Long id, String skuCode, String name, double weight,
               double turnoverRate, int priority, String category) {
        this.id = id;
        this.skuCode = skuCode;
        this.name = name;
        this.weight = weight;
        this.turnoverRate = turnoverRate;
        this.priority = priority;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public double getTurnoverRate() {
        return turnoverRate;
    }

    public void setTurnoverRate(double turnoverRate) {
        this.turnoverRate = turnoverRate;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getLength() {
        return length;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }
}
