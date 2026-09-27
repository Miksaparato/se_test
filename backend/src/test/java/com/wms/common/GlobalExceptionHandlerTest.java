package com.wms.common;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局异常处理器单元测试（a，T-3）。
 *
 * <p>验证《代码规范》4.2 要求的「异常 → 错误码 → HTTP 状态码」映射，
 * 以及 FR-RBAC-5 要求的 401/403 统一响应结构。纯 JUnit 5，不启动 Spring 上下文。
 *
 * @author a
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    private HttpServletRequest request;

    /**
     * 构造被测对象。
     */
    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("GET", "/api/v1/test");
    }

    @Test
    @DisplayName("业务异常：按错误码映射 HTTP 状态码并回填统一响应体")
    void should_map_biz_exception_to_http_status() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBizException(new BizException(ErrorCode.LOCATION_OCCUPIED), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(42201);
        assertThat(response.getBody().message()).isEqualTo("库位已占用");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    @DisplayName("业务异常：支持覆盖提示信息（用于补充上下文）")
    void should_keep_custom_message_of_biz_exception() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBizException(
                new BizException(ErrorCode.NOT_FOUND, "库位 A-01-03-02 不存在"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(40401);
        assertThat(response.getBody().message()).isEqualTo("库位 A-01-03-02 不存在");
    }

    @Test
    @DisplayName("越权异常：返回 403 与 40301（FR-RBAC-5）")
    void should_map_access_denied_to_403() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(40301);
        assertThat(response.getBody().message()).isEqualTo("无该操作权限");
    }

    @Test
    @DisplayName("认证异常：返回 401 与 40101")
    void should_map_authentication_to_401() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAuthentication(new BadCredentialsException("bad"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(40101);
    }

    @Test
    @DisplayName("参数校验失败：拼接多个字段提示并返回 400/40001")
    void should_join_field_errors_for_validation_failure() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("create", SampleRequest.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(
                new SampleRequest(null, null), "sampleRequest");
        bindingResult.addError(new FieldError("sampleRequest", "account", "账号不能为空"));
        bindingResult.addError(new FieldError("sampleRequest", "password", "密码不能为空"));
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValid(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(40001);
        assertThat(response.getBody().message()).contains("账号不能为空").contains("密码不能为空");
    }

    @Test
    @DisplayName("缺少必填参数：提示带参数名，返回 400/40001")
    void should_report_missing_request_parameter() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingParameter(
                new MissingServletRequestParameterException("file", "MultipartFile"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(40001);
        assertThat(response.getBody().message()).contains("file");
    }

    @Test
    @DisplayName("参数类型不匹配：提示带参数名，返回 400/40001")
    void should_report_type_mismatch() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("detail", Long.class);
        MethodParameter parameter = new MethodParameter(method, 0);

        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Long.class, "id", parameter, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("id");
    }

    @Test
    @DisplayName("请求体非法 JSON：返回 400 且提示须为合法 JSON")
    void should_report_unreadable_body() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotReadable(new HttpMessageNotReadableException("bad json"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(40001);
        assertThat(response.getBody().message()).contains("JSON");
    }

    @Test
    @DisplayName("请求方法不支持：返回 405（保持与《接口文档》1.3 一致）")
    void should_report_method_not_supported() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PATCH"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("请求方法");
    }

    @Test
    @DisplayName("请求路径不存在：返回 404 与 40412")
    void should_report_no_handler_found() throws Exception {
        NoHandlerFoundException exception = new NoHandlerFoundException(
                "GET", "/api/v1/not/exists", new org.springframework.http.HttpHeaders());

        ResponseEntity<ApiResponse<Void>> response = handler.handleNoHandler(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(40412);
        assertThat(response.getBody().message()).isEqualTo("请求的接口不存在");
    }

    @Test
    @DisplayName("数据库异常：返回 500 与 50001（不外泄 SQL 细节）")
    void should_report_data_access_exception() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataAccess(
                new DataIntegrityViolationException("foreign key constraint fails"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(50001);
        assertThat(response.getBody().message()).isEqualTo("数据库操作异常");
        assertThat(response.getBody().message()).doesNotContain("foreign key");
    }

    @Test
    @DisplayName("未预期异常：返回 500 与 50002，绝不外泄堆栈信息")
    void should_report_unexpected_exception_without_leaking_details() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new IllegalStateException("内部实现细节：连接池已满"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(50002);
        assertThat(response.getBody().message()).isEqualTo("系统异常，请稍后重试");
        assertThat(response.getBody().message()).doesNotContain("连接池");
    }

    @Test
    @DisplayName("全部错误码都能映射到合法 HTTP 状态码（防新增错误码时漏配）")
    void should_map_every_error_code_to_valid_http_status() {
        for (ErrorCode code : ErrorCode.values()) {
            HttpStatus status = ErrorCode.toHttpStatus(code.getCode());
            assertThat(status.isError())
                    .as("错误码 %s(%d) 应映射到 4xx/5xx", code.name(), code.getCode())
                    .isTrue();
            assertThat(status.value())
                    .as("错误码 %s 的前 3 位应等于 HTTP 状态码", code.name())
                    .isEqualTo(code.getCode() / 100);
        }
    }

    /** 采样请求对象，仅用于构造校验异常。 */
    private record SampleRequest(String account, String password) {
    }

    /** 采样控制器，仅用于获取 MethodParameter。 */
    private static class SampleController {

        /**
         * 采样方法：模拟 @RequestBody @Valid 入参。
         *
         * @param request 请求体
         */
        @SuppressWarnings("unused")
        void create(SampleRequest request) {
        }

        /**
         * 采样方法：模拟 @PathVariable 入参。
         *
         * @param id 资源 id
         */
        @SuppressWarnings("unused")
        void detail(Long id) {
        }
    }
}
