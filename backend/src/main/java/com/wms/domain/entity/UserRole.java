package com.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户-角色关联实体，对应表 {@code user_roles}（多对多）。
 *
 * @author a
 */
@TableName("user_roles")
public class UserRole {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id。 */
    private Long userId;

    /** 角色 id。 */
    private Long roleId;

    /** 创建时间(UTC)，插入时自动填充。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

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
     * 获取用户 id。
     *
     * @return 用户 id
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置用户 id。
     *
     * @param userId 用户 id
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取角色 id。
     *
     * @return 角色 id
     */
    public Long getRoleId() {
        return roleId;
    }

    /**
     * 设置角色 id。
     *
     * @param roleId 角色 id
     */
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
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

}
