package com.wms.data.role.vo;

import com.wms.domain.entity.Role;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色视图对象（API-010 ~ API-012）。
 *
 * @param id          角色 id
 * @param code        角色标识
 * @param name        角色名称
 * @param description 角色描述
 * @param isBuiltin   是否内置角色（true 时禁止删除，FR-RBAC-4）
 * @param userCount   关联用户数（用于前端提示「该角色仍被 N 个用户使用」）
 * @param permissions 权限码列表；为 null 表示本次未查询
 * @param createdAt   创建时间(UTC)
 * @author a
 */
public record RoleVO(
        Long id,
        String code,
        String name,
        String description,
        boolean isBuiltin,
        long userCount,
        List<String> permissions,
        LocalDateTime createdAt) {

    /**
     * 由实体构造。
     *
     * @param role        角色实体
     * @param userCount   关联用户数
     * @param permissions 权限码列表，可为 null
     * @return 角色视图对象
     */
    public static RoleVO from(Role role, long userCount, List<String> permissions) {
        return new RoleVO(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getIsBuiltin() != null && role.getIsBuiltin() == 1,
                userCount,
                permissions == null ? null : List.copyOf(permissions),
                role.getCreatedAt());
    }
}
