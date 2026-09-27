package com.wms.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.ApiResponse;
import com.wms.common.GlobalExceptionHandler;
import com.wms.config.WmsProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 鉴权中间件集成测试（a，A-B8 / T-3）：加载真实的 {@link SecurityConfig} 过滤器链与 MVC 链路，
 * 验证 FR-RBAC-3（后端强制鉴权）与 FR-RBAC-5（越权返回 403）。
 *
 * <p>说明：这里用 {@link AnnotationConfigWebApplicationContext} 显式装配所需 Bean，而不是
 * {@code @SpringBootTest}。原因是在受限（沙箱）环境下 Mockito 无法派生外部进程完成自附加，
 * 而 {@code @SpringBootTest} 会无条件注册 Mockito 监听器导致启动失败；本测试不需要任何 Mock，
 * 手工装配反而更贴近真实鉴权链路，也更轻量。
 * 真实 HTTP 端到端验证在应用启动后另行执行（见《鉴权中间件规范》验收步骤）。
 *
 * @author a
 */
class SecurityMiddlewareTest {

    private static final String SECRET = "wms-sim-unit-test-secret-key-0123456789abcdef";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static MockMvc mockMvc;

    private static JwtTokenProvider tokenProvider;

    /**
     * 装配 Spring 上下文（鉴权链 + 探针 Controller）并构建 MockMvc。
     */
    @BeforeAll
    static void setUp() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(SecurityTestContext.class, SecurityConfig.class, ProbeController.class);
        context.refresh();

        tokenProvider = context.getBean(JwtTokenProvider.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /**
     * 测试上下文：只声明鉴权链路所需的 Bean，不触碰数据库。
     *
     * <p>加 {@code @EnableWebMvc} 是为了获得与生产一致的消息转换器（Jackson），
     * 否则 {@code ApiResponse} 无法序列化，响应会变成 406。
     */
    @Configuration
    @EnableWebMvc
    static class SecurityTestContext {

        /**
         * 安全配置。
         *
         * @return 配置对象
         */
        @Bean
        WmsProperties wmsProperties() {
            WmsProperties properties = new WmsProperties();
            properties.getSecurity().setJwtSecret(SECRET);
            properties.getSecurity().setTokenExpireSeconds(3600L);
            return properties;
        }

        /**
         * Token 提供者。
         *
         * @param properties 安全配置
         * @return Token 提供者
         */
        @Bean
        JwtTokenProvider jwtTokenProvider(WmsProperties properties) {
            return new JwtTokenProvider(properties);
        }

        /**
         * 未认证入口（401）。
         *
         * @return 未认证入口
         */
        @Bean
        RestAuthenticationEntryPoint restAuthenticationEntryPoint() {
            return new RestAuthenticationEntryPoint(OBJECT_MAPPER);
        }

        /**
         * 越权处理器（403）。
         *
         * @return 越权处理器
         */
        @Bean
        RestAccessDeniedHandler restAccessDeniedHandler() {
            return new RestAccessDeniedHandler(OBJECT_MAPPER);
        }

        /**
         * JWT 过滤器。
         *
         * @param tokenProvider Token 提供者
         * @return JWT 过滤器
         */
        @Bean
        JwtAuthFilter jwtAuthFilter(JwtTokenProvider tokenProvider) {
            return new JwtAuthFilter(tokenProvider);
        }

        /**
         * 全局异常处理器。
         *
         * @return 全局异常处理器
         */
        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        /**
         * MVC 基础设施 Bean。
         *
         * <p>Spring Security 的 {@code requestMatchers(String...)} 在 Spring MVC 存在时会选用
         * {@code MvcRequestMatcher}，而它依赖该 Bean；Spring Boot 会自动提供，
         * 手工装配上下文时必须显式声明，否则过滤器链创建失败。
         *
         * @return HandlerMappingIntrospector
         */
        @Bean
        static org.springframework.web.servlet.handler.HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return new org.springframework.web.servlet.handler.HandlerMappingIntrospector();
        }
    }

    /**
     * 构造登录用户。
     *
     * @param account     账号
     * @param permissions 权限码集合
     * @return 登录用户
     */
    private LoginUser loginUser(String account, Set<String> permissions) {
        return new LoginUser(1L, account, "测试用户", Set.of("admin"), permissions);
    }

    @Test
    @DisplayName("未携带 Token：返回 401 与统一错误码 40101")
    void should_return_401_when_token_absent() throws Exception {
        mockMvc.perform(get("/api/v1/test/probe/allowed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andExpect(jsonPath("$.message").value("未登录或登录已失效，请重新登录"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("Token 为乱码：返回 401")
    void should_return_401_when_token_malformed() throws Exception {
        mockMvc.perform(get("/api/v1/test/probe/allowed")
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("Authorization 头缺少 Bearer 前缀：返回 401")
    void should_return_401_when_authorization_header_malformed() throws Exception {
        String token = tokenProvider.createToken(loginUser("operator", Set.of("sim:view")));

        mockMvc.perform(get("/api/v1/test/probe/allowed").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("Token 换密钥伪造：返回 401，绝不放过")
    void should_reject_forged_token() throws Exception {
        String forged = io.jsonwebtoken.Jwts.builder()
                .subject("hacker")
                .claim(JwtTokenProvider.CLAIM_USER_ID, 999L)
                .claim(JwtTokenProvider.CLAIM_PERMISSIONS, List.of("user:manage"))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                "another-secret-key-long-enough-000000000000".getBytes(StandardCharsets.UTF_8)),
                        io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/v1/test/probe/allowed")
                        .header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("Token 合法：放行，且 LoginUser 正确注入 SecurityContext")
    void should_pass_when_token_valid() throws Exception {
        String token = tokenProvider.createToken(loginUser("operator", Set.of("sim:view")));

        mockMvc.perform(get("/api/v1/test/probe/allowed")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.account").value("operator"))
                .andExpect(jsonPath("$.data.userId").value(1));
    }

    @Test
    @DisplayName("Token 合法但无所需权限：@PreAuthorize 返回 403 与错误码 40301（FR-RBAC-5）")
    void should_return_403_when_permission_missing() throws Exception {
        String token = tokenProvider.createToken(loginUser("viewer", Set.of("sim:view")));

        mockMvc.perform(get("/api/v1/test/probe/managed")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301))
                .andExpect(jsonPath("$.message").value("无该操作权限"));
    }

    @Test
    @DisplayName("Token 合法且权限匹配：@PreAuthorize 放行")
    void should_pass_when_permission_matched() throws Exception {
        String token = tokenProvider.createToken(loginUser("admin", Set.of("user:manage")));

        mockMvc.perform(get("/api/v1/test/probe/managed")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("未认证访问需要权限的接口：返回 401 而非 403（未登录优先于越权）")
    void should_return_401_before_403_when_anonymous() throws Exception {
        mockMvc.perform(get("/api/v1/test/probe/managed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("权限码必须完全一致，前缀相同的权限不得误判为匹配")
    void should_match_permission_code_exactly() throws Exception {
        String token = tokenProvider.createToken(loginUser("viewer", Set.of("user:manage:read")));

        mockMvc.perform(get("/api/v1/test/probe/managed")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("401 响应必须是 JSON，且不得出现 Basic 挑战头（前端按 code 处理）")
    void should_return_json_body_without_basic_challenge() throws Exception {
        var response = mockMvc.perform(get("/api/v1/test/probe/managed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101))
                .andReturn().getResponse();

        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        ApiResponse<?> body = OBJECT_MAPPER.readValue(
                response.getContentAsString(StandardCharsets.UTF_8), ApiResponse.class);
        assertThat(body.data()).isNull();
    }

    /**
     * 测试探针接口：一个只需登录，一个需要 {@code user:manage}。
     */
    @RestController
    @RequestMapping("/api/v1/test/probe")
    static class ProbeController {

        /**
         * 仅需登录即可访问，回显当前登录用户。
         *
         * @return 登录信息
         */
        @GetMapping("/allowed")
        public ApiResponse<Map<String, Object>> allowed() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            LoginUser loginUser = authentication != null && authentication.getPrincipal() instanceof LoginUser user
                    ? user : null;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("account", loginUser == null ? null : loginUser.getAccount());
            body.put("userId", loginUser == null ? null : loginUser.getUserId());
            body.put("permissions", loginUser == null ? List.of() : loginUser.getPermissions());
            return ApiResponse.ok(body);
        }

        /**
         * 需要 {@code user:manage} 权限。
         *
         * @return 固定成功响应
         */
        @GetMapping("/managed")
        @PreAuthorize("hasAuthority('user:manage')")
        public ApiResponse<String> managed() {
            return ApiResponse.ok("ok");
        }
    }
}
