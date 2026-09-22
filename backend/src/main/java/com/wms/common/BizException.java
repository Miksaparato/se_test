package com.wms.common;

/**
 * 业务异常：携带 {@link ErrorCode}，由 {@link GlobalExceptionHandler} 统一转换为 {@link ApiResponse}。
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /** 允许附加自定义错误信息（如「货物不存在: 3」）。 */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
