package com.wms.data.role.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 角色权限分配请求体（API-014），语义为**全量覆盖**。
 *
 * <p>示例（《接口文档》第 4 节）：
 * <pre>
 * PUT /api/v1/roles/3/permissions
 * { "permissions": ["sku:manage", "recommend:view", "sim:run", "sim:view"] }
 * </pre>
 *
 * @param permissions 权限码列表；必须全部来自《接口文档》1.4 清单。传空数组表示收回该角色全部权限
 * @author a
 */
public record AssignPermissionsRequest(
        @NotNull(message = "permissions 不能为 null，收回全部权限请传空数组")
        List<String> permissions) {
}
