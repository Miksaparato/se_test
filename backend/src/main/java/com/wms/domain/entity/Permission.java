package com.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 权限实体，对应表 {@code permissions}。
 *
 * <p>权限码必须来自《接口文档》1.4 清单，禁止自行发明（《代码规范》4.3）。
 *
 * @author a
 */
@TableName("permissions")
public class Permission {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限码，全局唯一，如 user:manage。 */
    private String code;

    /** 权限名称（中文）。 */
    private String name;

    /** 权限类型：menu 菜单 / action 操作。 */
    private String type;

    /** 展示排序。 */
    private Integer sortNo;

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
     * 获取权限码，全局唯一，如 user:manage。
     *
     * @return 权限码，全局唯一，如 user:manage
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置权限码，全局唯一，如 user:manage。
     *
     * @param code 权限码，全局唯一，如 user:manage
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 获取权限名称（中文）。
     *
     * @return 权限名称（中文）
     */
    public String getName() {
        return name;
    }

    /**
     * 设置权限名称（中文）。
     *
     * @param name 权限名称（中文）
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取权限类型：menu 菜单 / action 操作。
     *
     * @return 权限类型：menu 菜单 / action 操作
     */
    public String getType() {
        return type;
    }

    /**
     * 设置权限类型：menu 菜单 / action 操作。
     *
     * @param type 权限类型：menu 菜单 / action 操作
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取展示排序。
     *
     * @return 展示排序
     */
    public Integer getSortNo() {
        return sortNo;
    }

    /**
     * 设置展示排序。
     *
     * @param sortNo 展示排序
     */
    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
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
