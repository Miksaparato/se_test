package com.wms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.ApiResponse;
import com.wms.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证入口（401）：覆盖 Spring Security 默认的空响应体与 {@code WWW-Authenticate: Basic} 行为，
 * 统一输出《接口文档》1.2 的 JSON 结构（a，A-B8 / COM-4 契约）。
 *
 * <pre>
 * HTTP/1.1 401 Unauthorized
 * { "code": 40101, "message": "未登录或登录已失效，请重新登录", "data": null }
 * </pre>
 *
 * @author a
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(RestAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    /**
     * 构造未认证入口。
     *
     * @param objectMapper JSON 序列化器
     */
    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.debug("未认证访问被拦截 uri={} 原因={}", request.getRequestURI(), authException.getMessage());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(ErrorCode.UNAUTHORIZED));
    }
}
