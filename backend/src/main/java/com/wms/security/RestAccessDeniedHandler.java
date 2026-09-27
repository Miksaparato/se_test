package com.wms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.ApiResponse;
import com.wms.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 越权处理器（403），对应 FR-RBAC-5：已登录但无权限时返回统一 JSON 与 403 状态码。
 *
 * <pre>
 * HTTP/1.1 403 Forbidden
 * { "code": 40301, "message": "无该操作权限", "data": null }
 * </pre>
 *
 * <p>同时覆盖两类场景：
 * <ol>
 *   <li>过滤器链阶段的 URL 级拒绝（{@code authorizeHttpRequests}）；</li>
 *   <li>方法级 {@code @PreAuthorize} 拒绝（经 {@code ExceptionTranslationFilter} 转此处理）。</li>
 * </ol>
 *
 * @author a
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    /**
     * 构造越权处理器。
     *
     * @param objectMapper JSON 序列化器
     */
    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = authentication == null ? "anonymous" : authentication.getName();
        log.warn("越权访问被拦截 account={} uri={}", principal, request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(ErrorCode.FORBIDDEN));
    }
}
