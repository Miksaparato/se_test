package com.wms.data.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建角色请求体（API-011）。
 *
 * @param code        角色标识，唯一，小写字母开头
 * @param name        角色名称
 * @param description 角色描述
 * @author a
 */
public record CreateRoleRequest(
        @NotBlank(message = "角色标识不能为空")
        @Size(max = 32, message = "角色标识长度不能超过 32 个字符")
        @Pattern(regexp = "^[a-z][a-z0-9_-]{1,31}$", message = "角色标识须以小写字母开头，仅含小写字母、数字、下划线、连字符")
        String code,

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称长度不能超过 64 个字符")
        String name,

        @Size(max = 255, message = "角色描述长度不能超过 255 个字符")
        String description) {
}
