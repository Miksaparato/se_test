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
 * 统一响应封装，对应《接口文档》1.2 统一响应结构。
 *
 * <pre>
 * {
 *   "code": 0,
 *   "message": "success",
 *   "data": {}
 * }
 * </pre>
 *
 * <p>成功时 {@code code = 0}；失败时 {@code code} 为 {@link ErrorCode} 中的业务错误码，
 * {@code data} 为 {@code null}。
 *
 * @param code    0 表示成功，非 0 为业务错误码
 * @param message 提示信息
 * @param data    业务数据，可为对象、数组或 null
 * @param <T>     业务数据类型
 * @author a
 */
public record ApiResponse<T>(int code, String message, T data) {

    /** 成功响应码。 */
    public static final int SUCCESS_CODE = 0;

    /** 成功响应固定提示语。 */
    public static final String SUCCESS_MESSAGE = "success";

    /**
     * 构造成功响应。
     *
     * @param data 业务数据，可为 null
     * @param <T>  业务数据类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * 构造无数据的成功响应。
     *
     * @param <T> 业务数据类型
     * @return 成功响应，data 为 null
     */
    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    /**
     * 构造失败响应。
     *
     * @param code    业务错误码
     * @param message 提示信息
     * @param <T>     业务数据类型
     * @return 失败响应，data 为 null
     */
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /**
     * 按错误码枚举构造失败响应。
     *
     * @param errorCode 错误码枚举
     * @param <T>       业务数据类型
     * @return 失败响应，data 为 null
     */
    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return fail(errorCode.getCode(), errorCode.getMessage());
    }
}
