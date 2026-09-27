package com.wms.data.role.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新角色请求体（API-012）。
 *
 * <p>角色标识（{@code code}）为冻结契约的组成部分（权限矩阵、脚本、前端路由均按其判断），
 * 因此**不提供修改**，仅允许改名称与描述。
 *
 * @param name        角色名称
 * @param description 角色描述
 * @author a
 */
public record UpdateRoleRequest(
        @Size(max = 64, message = "角色名称长度不能超过 64 个字符")
        String name,

        @Size(max = 255, message = "角色描述长度不能超过 255 个字符")
        String description) {
}
