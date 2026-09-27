package com.wms.common;

/**
 * 业务异常：携带 {@link ErrorCode}，由 {@link GlobalExceptionHandler} 统一转换为 {@link ApiResponse}。
 */
public class BizException extends RuntimeException {

    private final int code;

 * 业务异常，由 {@link GlobalExceptionHandler} 统一转换为 {@link ApiResponse}。
 *
 * <p>使用方式（《代码规范》4.2）：
 * <pre>
 * throw new BizException(ErrorCode.WEIGHT_LAYER_VIOLATION);
 * throw new BizException(ErrorCode.NOT_FOUND, "库位 A-01-03-02 不存在");
 * </pre>
 *
 * @author a
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码。 */
    private final int code;

    /**
     * 按错误码枚举构造异常，提示信息取枚举默认值。
     *
     * @param errorCode 错误码枚举
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /** 允许附加自定义错误信息（如「货物不存在: 3」）。 */
    /**
     * 按错误码枚举构造异常，并覆盖提示信息。
     *
     * @param errorCode 错误码枚举
     * @param message   自定义提示信息
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /**
     * 按错误码枚举构造异常，并保留根因。
     *
     * @param errorCode 错误码枚举
     * @param message   自定义提示信息
     * @param cause     根因异常
     */
    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode.getCode();
    }

    /**
     * 获取业务错误码。
     *
     * @return 5 位业务错误码
     */
    public int getCode() {
        return code;
    }
}
