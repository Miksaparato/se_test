package com.wms.data.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改本人密码请求体（API-004）。
 *
 * @param oldPassword 原密码
 * @param newPassword 新密码
 * @author a
 */
public record ChangePasswordRequest(
        @NotBlank(message = "原密码不能为空")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度须为 6~64 个字符")
        String newPassword) {
}
