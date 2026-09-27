package com.wms.data.user.vo;

import com.wms.domain.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图对象。**绝不包含密码字段**（NFR-6）。
 *
 * @param id          用户 id
 * @param account     登录账号
 * @param name        姓名/昵称
 * @param email       邮箱
 * @param phone       手机号
 * @param status      账号状态：active / disabled
 * @param roles       角色标识列表，如 ["admin"]（《接口文档》API-001 返回结构）
 * @param permissions 权限码列表，如 ["user:manage"]（API-003 返回结构）
 * @param lastLoginAt 最近登录时间(UTC)
 * @param remark      备注
 * @param createdAt   创建时间(UTC)
 * @author a
 */
public record UserVO(
        Long id,
        String account,
        String name,
        String email,
        String phone,
        String status,
        List<String> roles,
        List<String> permissions,
        LocalDateTime lastLoginAt,
        String remark,
        LocalDateTime createdAt) {

    /**
     * 由实体构造（不含角色与权限，用于列表等场景）。
     *
     * @param user 用户实体
     * @return 用户视图对象
     */
    public static UserVO from(User user) {
        return from(user, List.of(), null);
    }

    /**
     * 由实体构造，附带角色与权限。
     *
     * @param user        用户实体
     * @param roles       角色标识列表
     * @param permissions 权限码列表，为 null 时返回 null（表示「未查询」）
     * @return 用户视图对象
     */
    public static UserVO from(User user, List<String> roles, List<String> permissions) {
        return new UserVO(
                user.getId(),
                user.getAccount(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                roles == null ? List.of() : List.copyOf(roles),
                permissions == null ? null : List.copyOf(permissions),
                user.getLastLoginAt(),
                user.getRemark(),
                user.getCreatedAt());
    }
}
