package com.wms.data.user.vo;

import com.wms.domain.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户列表项（API-006）。不含权限码与原密码。
 *
 * @param id          用户 id
 * @param account     登录账号
 * @param name        姓名
 * @param email       邮箱
 * @param phone       手机号
 * @param status      账号状态：active / disabled
 * @param roles       角色标识列表，如 ["operator"]
 * @param lastLoginAt 最近登录时间(UTC)
 * @param remark      备注
 * @param createdAt   创建时间(UTC)
 * @author a
 */
public record UserItemVO(
        Long id,
        String account,
        String name,
        String email,
        String phone,
        String status,
        List<String> roles,
        LocalDateTime lastLoginAt,
        String remark,
        LocalDateTime createdAt) {

    /**
     * 由实体构造。
     *
     * @param user  用户实体
     * @param roles 角色标识列表
     * @return 列表项
     */
    public static UserItemVO from(User user, List<String> roles) {
        return new UserItemVO(
                user.getId(),
                user.getAccount(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getStatus(),
                roles == null ? List.of() : List.copyOf(roles),
                user.getLastLoginAt(),
                user.getRemark(),
                user.getCreatedAt());
    }
}
