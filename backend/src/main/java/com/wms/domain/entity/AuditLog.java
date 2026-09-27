package com.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 操作审计日志实体，对应表 {@code audit_logs}。
 *
 * <p>《代码规范》4.6：关键路径（登录/登出/越权等）记录操作人、资源、结果；
 * 禁止写入密码、Token 等敏感信息。
 *
 * @author a
 */
@TableName("audit_logs")
public class AuditLog {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人 id。 */
    private Long userId;

    /** 操作人账号快照（用户删除后仍可追溯）。 */
    private String account;

    /** 行为：login/logout/create/update/delete/adopt/denied 等。 */
    private String action;

    /** 资源，如 skus/roles/locations。 */
    private String resource;

    /** 资源 id。 */
    private String resourceId;

    /** 详情 JSON 字符串（禁止写入密码、Token）。 */
    private String detail;

    /** 客户端 IP（兼容 IPv6）。 */
    private String ip;

    /** 发生时间(UTC)，插入时自动填充。 */
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
     * 获取操作人 id。
     *
     * @return 操作人 id
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置操作人 id。
     *
     * @param userId 操作人 id
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取操作人账号快照（用户删除后仍可追溯）。
     *
     * @return 操作人账号快照（用户删除后仍可追溯）
     */
    public String getAccount() {
        return account;
    }

    /**
     * 设置操作人账号快照（用户删除后仍可追溯）。
     *
     * @param account 操作人账号快照（用户删除后仍可追溯）
     */
    public void setAccount(String account) {
        this.account = account;
    }

    /**
     * 获取行为：login/logout/create/update/delete/adopt/denied 等。
     *
     * @return 行为：login/logout/create/update/delete/adopt/denied 等
     */
    public String getAction() {
        return action;
    }

    /**
     * 设置行为：login/logout/create/update/delete/adopt/denied 等。
     *
     * @param action 行为：login/logout/create/update/delete/adopt/denied 等
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * 获取资源，如 skus/roles/locations。
     *
     * @return 资源，如 skus/roles/locations
     */
    public String getResource() {
        return resource;
    }

    /**
     * 设置资源，如 skus/roles/locations。
     *
     * @param resource 资源，如 skus/roles/locations
     */
    public void setResource(String resource) {
        this.resource = resource;
    }

    /**
     * 获取资源 id。
     *
     * @return 资源 id
     */
    public String getResourceId() {
        return resourceId;
    }

    /**
     * 设置资源 id。
     *
     * @param resourceId 资源 id
     */
    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    /**
     * 获取详情 JSON 字符串（禁止写入密码、Token）。
     *
     * @return 详情 JSON 字符串（禁止写入密码、Token）
     */
    public String getDetail() {
        return detail;
    }

    /**
     * 设置详情 JSON 字符串（禁止写入密码、Token）。
     *
     * @param detail 详情 JSON 字符串（禁止写入密码、Token）
     */
    public void setDetail(String detail) {
        this.detail = detail;
    }

    /**
     * 获取客户端 IP（兼容 IPv6）。
     *
     * @return 客户端 IP（兼容 IPv6）
     */
    public String getIp() {
        return ip;
    }

    /**
     * 设置客户端 IP（兼容 IPv6）。
     *
     * @param ip 客户端 IP（兼容 IPv6）
     */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * 获取发生时间(UTC)，插入时自动填充。
     *
     * @return 发生时间(UTC)，插入时自动填充
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置发生时间(UTC)，插入时自动填充。
     *
     * @param createdAt 发生时间(UTC)，插入时自动填充
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}
