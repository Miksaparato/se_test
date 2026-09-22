package com.wms.common;

/**
 * 业务错误码枚举（对应《代码规范》4.2 错误码段）。
 * 错误码千位即 HTTP 状态码：400/401/403/404/422/500。
 */
public enum ErrorCode {

    PARAM_INVALID(40001, "参数校验失败"),
    WEIGHT_SUM_INVALID(40002, "权重之和必须为 1"),
    UNAUTHORIZED(40101, "Token 失效"),
    FORBIDDEN(40301, "无该操作权限"),
    NOT_FOUND(40401, "资源不存在"),
    LOCATION_OCCUPIED(42201, "库位已占用"),
    WEIGHT_LAYER_VIOLATION(42202, "重货层高违规"),
    NO_FREE_LOCATION(42203, "仓库暂无空闲库位"),
    NO_ELIGIBLE_LOCATION(42204, "无满足约束的库位"),
    EMPTY_RECOMMENDATION(42205, "推荐结果为空"),
    SYSTEM_ERROR(50001, "系统异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
