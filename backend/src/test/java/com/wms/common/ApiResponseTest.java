package com.wms.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ApiResponse} / {@link ErrorCode} / {@link PageResult} / {@link BizException} 脚手架单测。
 *
 * <p>属 T-3（数据与 RBAC 单元测试，负责人 a）的公共基础设施部分。
 *
 * @author a
 */
class ApiResponseTest {

    @Test
    @DisplayName("ok() 应返回 code=0 与 success 提示")
    void should_build_success_response() {
        ApiResponse<String> response = ApiResponse.ok("payload");

        assertThat(response.code()).isZero();
        assertThat(response.message()).isEqualTo(ApiResponse.SUCCESS_MESSAGE);
        assertThat(response.data()).isEqualTo("payload");
    }

    @Test
    @DisplayName("ok() 无参时 data 应为 null 且 code 仍为 0")
    void should_build_success_response_without_data() {
        ApiResponse<Void> response = ApiResponse.ok();

        assertThat(response.code()).isZero();
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("fail(ErrorCode) 应使用枚举的错误码与默认提示")
    void should_build_fail_response_from_error_code() {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.FORBIDDEN);

        assertThat(response.code()).isEqualTo(40301);
        assertThat(response.message()).isEqualTo("无该操作权限");
        assertThat(response.data()).isNull();
    }

    @Test
    @DisplayName("错误码前 3 位应映射到对应 HTTP 状态码")
    void should_map_error_code_to_http_status() {
        assertThat(ErrorCode.toHttpStatus(ErrorCode.PARAM_INVALID.getCode())).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.toHttpStatus(ErrorCode.UNAUTHORIZED.getCode())).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.toHttpStatus(ErrorCode.FORBIDDEN.getCode())).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.toHttpStatus(ErrorCode.NOT_FOUND.getCode())).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.toHttpStatus(ErrorCode.LOCATION_OCCUPIED.getCode()))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.toHttpStatus(ErrorCode.SYSTEM_ERROR.getCode()))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("非法错误码应兜底为 500，避免输出非法 HTTP 状态码")
    void should_fallback_to_500_for_unknown_error_code() {
        assertThat(ErrorCode.toHttpStatus(99999)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ErrorCode.toHttpStatus(1)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("全部错误码应落在 400/401/403/404/422/500 段内")
    void should_keep_all_error_codes_in_declared_segments() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(ErrorCode.toHttpStatus(code.getCode()))
                    .as("错误码 %s(%d)", code.name(), code.getCode())
                    .isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                            HttpStatus.NOT_FOUND, HttpStatus.UNPROCESSABLE_ENTITY,
                            HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Test
    @DisplayName("BizException 应携带错误码并支持覆盖提示信息")
    void should_carry_error_code_and_custom_message() {
        BizException defaultMessage = new BizException(ErrorCode.WEIGHT_LAYER_VIOLATION);
        assertThat(defaultMessage.getCode()).isEqualTo(42202);
        assertThat(defaultMessage.getMessage()).isEqualTo("重货层高违规");

        BizException custom = new BizException(ErrorCode.NOT_FOUND, "库位 A-01-03-02 不存在");
        assertThat(custom.getCode()).isEqualTo(40401);
        assertThat(custom.getMessage()).isEqualTo("库位 A-01-03-02 不存在");
        assertThatThrownBy(() -> {
            throw custom;
        }).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("分页参数规整：page 至少为 1，page_size 默认 20 且上限 100")
    void should_normalize_pagination_parameters() {
        assertThat(PageResult.normalizePage(null)).isEqualTo(1);
        assertThat(PageResult.normalizePage(0)).isEqualTo(1);
        assertThat(PageResult.normalizePage(-3)).isEqualTo(1);
        assertThat(PageResult.normalizePage(5)).isEqualTo(5);

        assertThat(PageResult.normalizePageSize(null)).isEqualTo(20);
        assertThat(PageResult.normalizePageSize(0)).isEqualTo(20);
        assertThat(PageResult.normalizePageSize(50)).isEqualTo(50);
        assertThat(PageResult.normalizePageSize(500)).isEqualTo(100);
    }

    @Test
    @DisplayName("empty() 应返回空列表且保留页码信息")
    void should_build_empty_page_result() {
        PageResult<String> empty = PageResult.empty(1, 20);

        assertThat(empty.list()).isEmpty();
        assertThat(empty.total()).isZero();
        assertThat(empty.page()).isEqualTo(1);
        assertThat(empty.page_size()).isEqualTo(20);
    }
}
