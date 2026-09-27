package com.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 出库订单实体，对应表 {@code orders}。
 *
 * <p>一条订单一条 SKU 明细（与《需求文档》6.1 一致）。
 * 出库仿真排序为「优先级降序 + 下达时间升序」（FR-4.1），
 * 对应索引 {@code idx_orders_status_priority_placed(status, priority DESC, placed_at ASC)}。
 *
 * @author a
 */
@TableName("orders")
public class Order {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号，全局唯一，如 SO-20260910-001。 */
    private String orderNo;

    /** 货物 id。 */
    private Long skuId;

    /** 出库数量。 */
    private Integer quantity;

    /** 订单优先级 1~5，越大越紧急。 */
    private Integer priority;

    /** 下达时间(UTC)。 */
    private LocalDateTime placedAt;

    /** 状态：pending 待出库 / picking 拣选中 / completed 已完成 / cancelled 已取消。 */
    private String status;

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

    /**
     * 获取订单号，全局唯一，如 SO-20260910-001。
     *
     * @return 订单号，全局唯一，如 SO-20260910-001
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * 设置订单号，全局唯一，如 SO-20260910-001。
     *
     * @param orderNo 订单号，全局唯一，如 SO-20260910-001
     */
    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    /**
     * 获取货物 id。
     *
     * @return 货物 id
     */
    public Long getSkuId() {
        return skuId;
    }

    /**
     * 设置货物 id。
     *
     * @param skuId 货物 id
     */
    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    /**
     * 获取出库数量。
     *
     * @return 出库数量
     */
    public Integer getQuantity() {
        return quantity;
    }

    /**
     * 设置出库数量。
     *
     * @param quantity 出库数量
     */
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    /**
     * 获取订单优先级 1~5，越大越紧急。
     *
     * @return 订单优先级 1~5，越大越紧急
     */
    public Integer getPriority() {
        return priority;
    }

    /**
     * 设置订单优先级 1~5，越大越紧急。
     *
     * @param priority 订单优先级 1~5，越大越紧急
     */
    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    /**
     * 获取下达时间(UTC)。
     *
     * @return 下达时间(UTC)
     */
    public LocalDateTime getPlacedAt() {
        return placedAt;
    }

    /**
     * 设置下达时间(UTC)。
     *
     * @param placedAt 下达时间(UTC)
     */
    public void setPlacedAt(LocalDateTime placedAt) {
        this.placedAt = placedAt;
    }

    /**
     * 获取状态：pending 待出库 / picking 拣选中 / completed 已完成 / cancelled 已取消。
     *
     * @return 状态：pending 待出库 / picking 拣选中 / completed 已完成 / cancelled 已取消
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置状态：pending 待出库 / picking 拣选中 / completed 已完成 / cancelled 已取消。
     *
     * @param status 状态：pending 待出库 / picking 拣选中 / completed 已完成 / cancelled 已取消
     */
    public void setStatus(String status) {
        this.status = status;
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
