package com.wms.security;

import com.wms.config.WmsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 鉴权中间件配置（a，A-B8），对应《代码规范》4.3 与《接口文档》1.3。
 *
 * <p>核心约定（冻结契约 COM-4，b、c 使用方式见《鉴权中间件规范》）：
 * <ol>
 *   <li>无状态会话：不建 Session，认证信息全部来自 JWT；</li>
 *   <li>白名单仅登录接口 {@code /api/v1/auth/login}，<b>其余接口一律需要认证</b>；</li>
 *   <li>业务接口用 {@code @PreAuthorize("hasAuthority('权限码')")} 声明权限，
 *       权限码必须来自《接口文档》1.4 清单；</li>
 *   <li>未认证统一返回 401 + {@code {"code":40101,...}}；越权统一返回 403 + {@code {"code":40301,...}}；</li>
 *   <li>关闭 CSRF 与默认表单登录/HTTP Basic，避免前端收到 302 重定向或 Basic 挑战。</li>
 * </ol>
 *
 * @author a
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 用户/角色/权限管理权限码（《接口文档》1.4）。 */
    private static final String PERMISSION_USER_MANAGE = "user:manage";

    /** 仓库/货架/库位管理权限码。 */
    private static final String PERMISSION_WAREHOUSE_MANAGE = "warehouse:manage";

    /** 货物/订单管理权限码。 */
    private static final String PERMISSION_SKU_MANAGE = "sku:manage";

    /** 库位智能推荐权限码。 */
    private static final String PERMISSION_RECOMMEND_VIEW = "recommend:view";

    /** 入库策略仿真权限码。 */
    private static final String PERMISSION_SIM_RUN = "sim:run";

    /** 仿真结果查看权限码。 */
    private static final String PERMISSION_SIM_VIEW = "sim:view";

    /** 方案对比与优化建议权限码。 */
    private static final String PERMISSION_COMPARE_VIEW = "compare:view";

    /** 报告导出权限码。 */
    private static final String PERMISSION_REPORT_EXPORT = "report:export";

    /** 系统配置权限码。 */
    private static final String PERMISSION_CONFIG_MANAGE = "config:manage";

    private final JwtAuthFilter jwtAuthFilter;

    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    private final RestAccessDeniedHandler accessDeniedHandler;

    private final WmsProperties properties;

    /**
     * 构造安全配置。
     *
     * @param jwtAuthFilter           JWT 过滤器
     * @param authenticationEntryPoint 未认证入口（401）
     * @param accessDeniedHandler     越权处理器（403）
     * @param properties              自定义安全配置
     */
    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          WmsProperties properties) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.properties = properties;
    }

    /**
     * 安全过滤器链。
     *
     * @param http HttpSecurity 构建器
     * @return 过滤器链
     * @throws Exception 构建失败
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        String loginPath = properties.getSecurity().getLoginPath();
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 预检请求放行，避免跨域预检被鉴权拦截
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 登录接口免鉴权（API-001）
                        .requestMatchers(loginPath).permitAll()
                        /*
                         * ================== 逐接口权限映射（《接口文档》1.4）==================
                         * 为什么在过滤器链上重复声明一遍权限，而不是只依赖方法级 @PreAuthorize：
                         *   方法级鉴权（AOP）的执行时机在 @Valid 参数校验**之后**。若只在方法上加注解，
                         *   越权者带一个非法请求体调用接口时，会先收到 400 参数校验失败（暴露参数结构），
                         *   而不是 403 无该操作权限 —— 不满足 FR-RBAC-5「越权一律 403」。
                         *   过滤器链上的规则在进入 DispatcherServlet 之前生效，因此越权请求一律 403。
                         * 方法级 @PreAuthorize 保留为纵深防御的第二道防线。
                         * 变更权限映射须同步更新《鉴权中间件规范》与《接口文档》。
                         */

                        // 认证模块：登录即可（登录接口已白名单）
                        .requestMatchers("/api/v1/auth/**").authenticated()

                        // RBAC 管理（API-005~015）：user:manage
                        .requestMatchers("/api/v1/users/**", "/api/v1/roles/**", "/api/v1/permissions/**")
                        .hasAuthority(PERMISSION_USER_MANAGE)

                        // 仓库/货架/库位：读 sim:view（API-017/018/021/027），写 warehouse:manage（API-016/019/020/022~026/028）
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/warehouses/**", "/api/v1/racks/**", "/api/v1/locations/**")
                        .hasAuthority(PERMISSION_SIM_VIEW)
                        .requestMatchers("/api/v1/warehouses/**", "/api/v1/racks/**", "/api/v1/locations/**")
                        .hasAuthority(PERMISSION_WAREHOUSE_MANAGE)

                        // 数据导入导出（API-037~040）
                        .requestMatchers("/api/v1/import/**").hasAuthority(PERMISSION_SKU_MANAGE)
                        .requestMatchers("/api/v1/export/**").hasAuthority(PERMISSION_SIM_VIEW)

                        // 随机测试订单集（API-062）必须排在 orders 通配之前（sim:run，仅 c）
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/generate")
                        .hasAuthority(PERMISSION_SIM_RUN)

                        // 货物（API-029~032）：读 sim:view，写 sku:manage
                        .requestMatchers(HttpMethod.GET, "/api/v1/skus/**").hasAuthority(PERMISSION_SIM_VIEW)
                        .requestMatchers("/api/v1/skus/**").hasAuthority(PERMISSION_SKU_MANAGE)

                        // 订单（API-033~036）：读 sim:view，写 sku:manage
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/**").hasAuthority(PERMISSION_SIM_VIEW)
                        .requestMatchers("/api/v1/orders/**").hasAuthority(PERMISSION_SKU_MANAGE)

                        // 库位智能推荐（API-041~043）：recommend:view
                        .requestMatchers("/api/v1/recommendations/**").hasAuthority(PERMISSION_RECOMMEND_VIEW)

                        // 权重与规则配置（API-044~048）：config:manage
                        .requestMatchers("/api/v1/config/**").hasAuthority(PERMISSION_CONFIG_MANAGE)

                        // 方案对比与建议（API-049~052）：compare:view；报告导出（API-053）：report:export
                        .requestMatchers(HttpMethod.GET, "/api/v1/plans/*/export")
                        .hasAuthority(PERMISSION_REPORT_EXPORT)
                        .requestMatchers("/api/v1/plans/**").hasAuthority(PERMISSION_COMPARE_VIEW)

                        // 入库/出库仿真：写 sim:run（API-054/055/057/058），读 sim:view（API-056/059~061）
                        .requestMatchers(HttpMethod.GET, "/api/v1/strategies", "/api/v1/simulations/**")
                        .hasAuthority(PERMISSION_SIM_VIEW)
                        .requestMatchers("/api/v1/strategies", "/api/v1/simulations/**")
                        .hasAuthority(PERMISSION_SIM_RUN)

                        // 其余接口（如 /api/v1/health）只需登录
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 密码编码器：BCrypt（strength = 10，与 V3 种子数据哈希一致），NFR-6 禁止明文存储。
     *
     * @return 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 跨域配置：允许前端开发服务器（Vite 默认 5173）携带 Authorization 头访问。
     *
     * @return 跨域配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
