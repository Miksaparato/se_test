package com.wms.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理：将业务异常与框架异常统一为 {@link ApiResponse}，
 * 并按错误码千位映射 HTTP 状态码（对应《接口文档》1.3）。
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器，把各类异常统一转换为 {@link ApiResponse} 并映射 HTTP 状态码
 * （《代码规范》4.2、《接口文档》1.3）。
 *
 * <p>未认证（401）与越权（403）也可能由 Spring Security 过滤器链在进入 Controller 之前抛出，
 * 此处仅兜底处理进入 MVC 之后的同类异常；过滤器链阶段的响应由 a 的 A-B8 鉴权中间件统一输出。
 *
 * @author a
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        return ResponseEntity.status(toHttp(e.getCode()))
                .body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldErrors().get(0);
        String msg = fe == null ? ErrorCode.PARAM_INVALID.getMessage()
                : fe.getField() + " " + fe.getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), msg));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), "请求参数错误: " + e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage()));
    }

    private HttpStatus toHttp(int code) {
        return switch (code / 100) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 422 -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    /**
     * 业务异常：按错误码映射 HTTP 状态码。
     *
     * @param ex      业务异常
     * @param request 当前请求（用于日志定位）
     * @return 统一失败响应
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex, HttpServletRequest request) {
        log.warn("业务异常 code={} message={} uri={}", ex.getCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ErrorCode.toHttpStatus(ex.getCode()))
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * 请求体参数校验失败（{@code @RequestBody @Valid}）。
     *
     * @param ex 校验异常
     * @return 400 统一失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        if (detail.isBlank()) {
            detail = ErrorCode.PARAM_INVALID.getMessage();
        }
        log.warn("参数校验失败：{}", detail);
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), detail));
    }

    /**
     * 表单/查询参数绑定校验失败。
     *
     * @param ex 绑定异常
     * @return 400 统一失败响应
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        if (detail.isBlank()) {
            detail = ErrorCode.PARAM_INVALID.getMessage();
        }
        log.warn("参数绑定失败：{}", detail);
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), detail));
    }

    /**
     * 缺少必填请求参数。
     *
     * @param ex 缺少参数异常
     * @return 400 统一失败响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        String detail = "缺少必填参数：" + ex.getParameterName();
        log.warn(detail);
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), detail));
    }

    /**
     * 参数类型不匹配（如 id 传入非数字）。
     *
     * @param ex 类型不匹配异常
     * @return 400 统一失败响应
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = "参数类型不正确：" + ex.getName();
        log.warn(detail);
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), detail));
    }

    /**
     * 请求体不是合法 JSON 或无法反序列化。
     *
     * @param ex 报文解析异常
     * @return 400 统一失败响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败：{}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), "请求体格式错误，须为合法 JSON"));
    }

    /**
     * 请求方法不支持。
     *
     * @param ex 方法不支持异常
     * @return 405 统一失败响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("请求方法不支持：{}", ex.getMessage());
        return ResponseEntity.status(405)
                .body(ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), "请求方法不支持"));
    }

    /**
     * 请求路径不存在。
     *
     * @param ex 无处理器异常
     * @return 404 统一失败响应
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoHandler(NoHandlerFoundException ex) {
        log.warn("接口不存在：{}", ex.getRequestURL());
        return ResponseEntity.status(404).body(ApiResponse.fail(ErrorCode.API_NOT_FOUND));
    }

    /**
     * 认证失败（未登录或 Token 失效）。
     *
     * @param ex 认证异常
     * @return 401 统一失败响应
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("认证失败：{}", ex.getMessage());
        return ResponseEntity.status(401).body(ApiResponse.fail(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 越权访问（已登录但无权限），对应 FR-RBAC-5。
     *
     * @param ex 授权异常
     * @return 403 统一失败响应
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("越权访问被拦截：{}", ex.getMessage());
        return ResponseEntity.status(403).body(ApiResponse.fail(ErrorCode.FORBIDDEN));
    }

    /**
     * 数据库访问异常。
     *
     * @param ex 数据访问异常
     * @return 500 统一失败响应
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException ex) {
        log.error("数据库操作异常", ex);
        return ResponseEntity.status(500).body(ApiResponse.fail(ErrorCode.DATABASE_ERROR));
    }

    /**
     * 兜底异常处理。
     *
     * @param ex 未预期异常
     * @return 500 统一失败响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("系统未预期异常", ex);
        return ResponseEntity.status(500).body(ApiResponse.fail(ErrorCode.SYSTEM_ERROR));
    }

    /**
     * 格式化字段校验错误为「字段: 提示」。
     *
     * @param fieldError 字段错误
     * @return 可读的错误描述
     */
    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
