package com.wms.data.user.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 用户启停请求体（API-008）。
 *
 * @param status 目标状态：active 启用 / disabled 禁用
 * @author a
 */
public record UpdateUserStatusRequest(
        @Pattern(regexp = "^(active|disabled)$", message = "账号状态只能是 active 或 disabled")
        String status) {
}
