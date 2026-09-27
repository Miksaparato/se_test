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

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.wms.domain.entity.type.SkuSize;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 货物（SKU）实体，对应表 {@code skus}。
 *
 * <p><b>核心输入契约</b>：{@code weight} / {@code turnoverRate} / {@code priority}
 * 是推荐引擎（b）评分与仿真引擎（c）选位的核心依据（《接口文档》第 6 节标注）。
 *
 * <p>{@code size} 为 JSON 列，反序列化为 {@code {"length":30,"width":20,"height":10}}，
 * 与《接口文档》API-030 请求示例完全一致。
 *
 * @author a
 */
@TableName(value = "skus", autoResultMap = true)
public class Sku {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** SKU 编码，全局唯一，如 SKU-001。 */
    private String code;

    /** 货物名称。 */
    private String name;

    /** 重量(kg)，评分 S_weight 输入。 */
    private BigDecimal weight;

    /** 周转频次(次/单位时间)，评分 S_freq 输入。 */
    private BigDecimal turnoverRate;

    /** 出库优先级 1~5，越大越紧急，评分 S_priority 输入。 */
    private Integer priority;

    /** 品类，评分 S_other 分区匹配项。 */
    private String category;

    /** 尺寸（JSON 列）。 */
    @TableField(value = "size", typeHandler = JacksonTypeHandler.class)
    private SkuSize size;

    /** 备注。 */
    private String remark;

    /** 创建时间(UTC)，插入时自动填充。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间(UTC)，插入与更新时自动填充。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 获取主键。
     *
     * @return 主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键。
     *
     * @param id 主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    public String getSkuCode() {
        return skuCode;
    }

    public void setSkuCode(String skuCode) {
        this.skuCode = skuCode;
    }

    /**
     * 获取SKU 编码，全局唯一，如 SKU-001。
     *
     * @return SKU 编码，全局唯一，如 SKU-001
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置SKU 编码，全局唯一，如 SKU-001。
     *
     * @param code SKU 编码，全局唯一，如 SKU-001
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 获取货物名称。
     *
     * @return 货物名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置货物名称。
     *
     * @param name 货物名称
     */
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

    /**
     * 获取重量(kg)，评分 S_weight 输入。
     *
     * @return 重量(kg)，评分 S_weight 输入
     */
    public BigDecimal getWeight() {
        return weight;
    }

    /**
     * 设置重量(kg)，评分 S_weight 输入。
     *
     * @param weight 重量(kg)，评分 S_weight 输入
     */
    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    /**
     * 获取周转频次(次/单位时间)，评分 S_freq 输入。
     *
     * @return 周转频次(次/单位时间)，评分 S_freq 输入
     */
    public BigDecimal getTurnoverRate() {
        return turnoverRate;
    }

    /**
     * 设置周转频次(次/单位时间)，评分 S_freq 输入。
     *
     * @param turnoverRate 周转频次(次/单位时间)，评分 S_freq 输入
     */
    public void setTurnoverRate(BigDecimal turnoverRate) {
        this.turnoverRate = turnoverRate;
    }

    /**
     * 获取出库优先级 1~5，越大越紧急，评分 S_priority 输入。
     *
     * @return 出库优先级 1~5，越大越紧急，评分 S_priority 输入
     */
    public Integer getPriority() {
        return priority;
    }

    /**
     * 设置出库优先级 1~5，越大越紧急，评分 S_priority 输入。
     *
     * @param priority 出库优先级 1~5，越大越紧急，评分 S_priority 输入
     */
    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    /**
     * 获取品类，评分 S_other 分区匹配项。
     *
     * @return 品类，评分 S_other 分区匹配项
     */
    public String getCategory() {
        return category;
    }

    /**
     * 设置品类，评分 S_other 分区匹配项。
     *
     * @param category 品类，评分 S_other 分区匹配项
     */
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
    /**
     * 获取尺寸（JSON 列）。
     *
     * @return 尺寸（JSON 列）
     */
    public SkuSize getSize() {
        return size;
    }

    /**
     * 设置尺寸（JSON 列）。
     *
     * @param size 尺寸（JSON 列）
     */
    public void setSize(SkuSize size) {
        this.size = size;
    }

    /**
     * 获取备注。
     *
     * @return 备注
     */
    public String getRemark() {
        return remark;
    }

    /**
     * 设置备注。
     *
     * @param remark 备注
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * 获取创建时间(UTC)，插入时自动填充。
     *
     * @return 创建时间(UTC)，插入时自动填充
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间(UTC)，插入时自动填充。
     *
     * @param createdAt 创建时间(UTC)，插入时自动填充
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取更新时间(UTC)，插入与更新时自动填充。
     *
     * @return 更新时间(UTC)，插入与更新时自动填充
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间(UTC)，插入与更新时自动填充。
     *
     * @param updatedAt 更新时间(UTC)，插入与更新时自动填充
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

}
