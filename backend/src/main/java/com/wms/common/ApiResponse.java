package com.wms.common;

/**
 * 统一响应结构（对应《接口文档》1.2）：
 * <pre>{ "code": 0, "message": "success", "data": {} }</pre>
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
