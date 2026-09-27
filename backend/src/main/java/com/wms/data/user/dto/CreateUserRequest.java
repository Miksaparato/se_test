package com.wms.data.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建用户请求体（API-005，仅管理员）。
 *
 * @param account  登录账号，唯一
 * @param password 初始密码，服务端 BCrypt 加密后存储
 * @param name     姓名/昵称
 * @param email    邮箱
 * @param phone    手机号
 * @param status   账号状态：active / disabled，缺省 active
 * @param roles    角色标识列表，如 ["operator"]；缺省为 ["viewer"]
 * @param remark   备注
 * @author a
 */
public record CreateUserRequest(
        @NotBlank(message = "账号不能为空")
        @Size(max = 32, message = "账号长度不能超过 32 个字符")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "账号只能包含字母、数字、下划线、点与连字符")
        String account,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度须为 6~64 个字符")
        String password,

        @Size(max = 64, message = "姓名长度不能超过 64 个字符")
        String name,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
        String email,

        @Size(max = 32, message = "手机号长度不能超过 32 个字符")
        String phone,

        @Pattern(regexp = "^(active|disabled)$", message = "账号状态只能是 active 或 disabled")
        String status,

        List<String> roles,

        @Size(max = 255, message = "备注长度不能超过 255 个字符")
        String remark) {
}
