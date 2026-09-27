package com.wms.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 鉴权过滤器（a，A-B8）：解析 {@code Authorization: Bearer <token>}，
 * 将用户与权限码装入 SecurityContext，供 {@code @PreAuthorize} 校验（《代码规范》4.3）。
 *
 * <p>行为约定：
 * <ul>
 *   <li>无 Token 或 Token 非法：<b>不在此处直接返回</b>，交由 Spring Security 的
 *       {@link org.springframework.security.web.AuthenticationEntryPoint} 统一输出 401；</li>
 *   <li>Token 合法：写入认证信息，且<b>不覆盖</b>已存在的认证（避免重复认证）；</li>
 *   <li>始终放行请求，不在过滤器中抛业务异常。</li>
 * </ul>
 *
 * @author a
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    /** Bearer 前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    /**
     * 构造过滤器。
     *
     * @param tokenProvider Token 提供者
     */
    public JwtAuthFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = tokenProvider.parseClaims(token);
                LoginUser loginUser = tokenProvider.toLoginUser(claims);
                if (loginUser != null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    loginUser, null, loginUser.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("鉴权通过 account={} uri={}", loginUser.getAccount(), request.getRequestURI());
                }
            } catch (Exception ex) {
                // Token 非法/过期：清空上下文，后续由 AuthenticationEntryPoint 统一返回 401
                SecurityContextHolder.clearContext();
                log.debug("Token 解析失败 uri={} 原因={}", request.getRequestURI(), ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头解析 Bearer Token。
     *
     * @param request 当前请求
     * @return Token 字符串；不存在时返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
