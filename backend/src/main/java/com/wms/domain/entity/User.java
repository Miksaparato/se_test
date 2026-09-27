package com.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 {@code users}。
 *
 * <p>{@code password} 存 BCrypt 哈希，标注 {@link JsonIgnore} 作为兜底，
 * 但对外一律使用 VO，绝不直接序列化本实体（NFR-6）。
 *
 * @author a
 */
@TableName("users")
public class User {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号，全局唯一。 */
    private String account;

    /** 密码 BCrypt 哈希。 */
    @JsonIgnore
    private String password;

    /** 姓名/昵称。 */
    private String name;

    /** 邮箱。 */
    private String email;

    /** 手机号。 */
    private String phone;

    /** 账号状态：active / disabled。 */
    private String status;

    /** 最近登录时间(UTC)。 */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    /** 备注。 */
    private String remark;

    /** 创建人用户 id。 */
    @TableField("created_by")
    private Long createdBy;

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
     * 获取登录账号。
     *
     * @return 登录账号
     */
    public String getAccount() {
        return account;
    }

    /**
     * 设置登录账号。
     *
     * @param account 登录账号
     */
    public void setAccount(String account) {
        this.account = account;
    }

    /**
     * 获取密码哈希。
     *
     * @return 密码哈希
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置密码哈希。
     *
     * @param password 密码哈希
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取姓名。
     *
     * @return 姓名
     */
    public String getName() {
        return name;
    }

    /**
     * 设置姓名。
     *
     * @param name 姓名
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取邮箱。
     *
     * @return 邮箱
     */
    public String getEmail() {
        return email;
    }

    /**
     * 设置邮箱。
     *
     * @param email 邮箱
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * 获取手机号。
     *
     * @return 手机号
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 设置手机号。
     *
     * @param phone 手机号
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * 获取账号状态。
     *
     * @return active / disabled
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置账号状态。
     *
     * @param status active / disabled
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取最近登录时间。
     *
     * @return 最近登录时间
     */
    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    /**
     * 设置最近登录时间。
     *
     * @param lastLoginAt 最近登录时间
     */
    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
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
     * 获取创建人 id。
     *
     * @return 创建人用户 id
     */
    public Long getCreatedBy() {
        return createdBy;
    }

    /**
     * 设置创建人 id。
     *
     * @param createdBy 创建人用户 id
     */
    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取更新时间。
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间。
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
