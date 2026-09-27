package com.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 角色实体，对应表 {@code roles}。
 *
 * <p>{@code is_builtin = 1} 的内置角色禁止删除（FR-RBAC-4），但其权限可被管理员调整。
 *
 * @author a
 */
@TableName("roles")
public class Role {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色标识，全局唯一：admin/operator/analyst/viewer。 */
    private String code;

    /** 角色名称（中文）。 */
    private String name;

    /** 角色描述。 */
    private String description;

    /** 是否内置角色：1 内置(禁删) / 0 自定义。 */
    private Integer isBuiltin;

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
     * 获取角色标识，全局唯一：admin/operator/analyst/viewer。
     *
     * @return 角色标识，全局唯一：admin/operator/analyst/viewer
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置角色标识，全局唯一：admin/operator/analyst/viewer。
     *
     * @param code 角色标识，全局唯一：admin/operator/analyst/viewer
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 获取角色名称（中文）。
     *
     * @return 角色名称（中文）
     */
    public String getName() {
        return name;
    }

    /**
     * 设置角色名称（中文）。
     *
     * @param name 角色名称（中文）
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取角色描述。
     *
     * @return 角色描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置角色描述。
     *
     * @param description 角色描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取是否内置角色：1 内置(禁删) / 0 自定义。
     *
     * @return 是否内置角色：1 内置(禁删) / 0 自定义
     */
    public Integer getIsBuiltin() {
        return isBuiltin;
    }

    /**
     * 设置是否内置角色：1 内置(禁删) / 0 自定义。
     *
     * @param isBuiltin 是否内置角色：1 内置(禁删) / 0 自定义
     */
    public void setIsBuiltin(Integer isBuiltin) {
        this.isBuiltin = isBuiltin;
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
