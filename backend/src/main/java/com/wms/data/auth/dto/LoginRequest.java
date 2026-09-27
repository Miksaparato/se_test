package com.wms.data.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求体（API-001）。
 *
 * @param account  登录账号
 * @param password 明文密码，服务端用 BCrypt 校验
 * @author a
 */
public record LoginRequest(
        @NotBlank(message = "账号不能为空")
        @Size(max = 32, message = "账号长度不能超过 32 个字符")
        String account,

        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码长度不能超过 64 个字符")
        String password) {
}
