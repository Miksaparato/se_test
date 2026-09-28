package com.wms.security;

import com.wms.WmsApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 鉴权中间件真实 HTTP 端到端测试（a，A-B8）：用 {@link SpringApplicationBuilder} 以随机端口
 * 程序化启动**真实应用**（真实过滤器链、真实 DispatcherServlet、真实 Jackson 序列化），
 * 通过 HTTP 验证 401 / 放行 / 403 / 404 四类结果。
 *
 * <p>标记 {@code e2e}，默认构建排除；执行：{@code mvn -s maven-settings.xml test -Pe2e}。
 *
 * <p>为什么不用 {@code @SpringBootTest}：该注解会无条件注册 Mockito 上下文定制器，
 * 而受限环境下 Mockito 无法派生外部进程完成自附加，会导致上下文加载失败。
 * 程序化启动既避开该限制，又比 MockMvc 更接近生产（真实 TCP + 真实容器）。
 *
 * @author a
 */
@Tag("e2e")
class SecurityE2eTest {

    private static final String SECRET = "wms-sim-e2e-secret-key-0123456789abcdefghijklmn";

    private static ConfigurableApplicationContext context;

    private static String baseUrl;

    private static JwtTokenProvider tokenProvider;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 启动真实应用（随机端口、H2 内存库、探针 Controller）。
     *
     * <p><b>为什么用 {@code run("--key=value")} 而不是 {@code .properties(...)}</b>：
     * {@code SpringApplicationBuilder.properties()} 设置的是**默认属性**，优先级最低，
     * 会被 {@code application.yml} 里的同名配置覆盖——也就是说这些用例会连到
     * `application.yml` 指向的真实库 {@code wms_sim}，既污染开发数据，也失去了
     * 「用 H2 占位、不依赖 MySQL」的本意。命令行参数优先级最高，才能确保生效。
     */
    @BeforeAll
    static void startApplication() {
        context = new SpringApplicationBuilder(WmsApplication.class, ProbeController.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--wms.security.jwt-secret=" + SECRET,
                        "--wms.security.token-expire-seconds=3600",
                        // 本测试只验证鉴权链路，不依赖真实 MySQL：用 H2 占位并关闭 Flyway
                        "--spring.datasource.url=jdbc:h2:mem:wms_e2e;MODE=MySQL;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.flyway.enabled=false",
                        "--spring.mvc.throw-exception-if-no-handler-found=true",
                        "--spring.web.resources.add-mappings=false",
                        "--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl",
                        "--logging.level.root=WARN");

        int port = context.getEnvironment().getProperty("local.server.port", Integer.class, 8081);
        baseUrl = "http://127.0.0.1:" + port;
        tokenProvider = context.getBean(JwtTokenProvider.class);
    }

    /**
     * 关闭应用。
     */
    @AfterAll
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * 发送 GET 请求。
     *
     * @param path  路径
     * @param token JWT，为 null 时不带 Authorization 头
     * @return HTTP 响应（字符串体）
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 构造登录用户。
     *
     * @param account     账号
     * @param permissions 权限码
     * @return 登录用户
     */
    private LoginUser loginUser(String account, Set<String> permissions) {
        return new LoginUser(1L, account, "测试用户", Set.of("operator"), permissions);
    }

    @Test
    @DisplayName("未带 Token：真实 HTTP 返回 401、code=40101、JSON 体，且无 Basic 挑战头")
    void should_return_401_over_real_http() throws Exception {
        HttpResponse<String> response = get("/api/v1/warehouses", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("application/json");
        assertThat(response.body()).contains("\"code\":40101");
        assertThat(response.body()).contains("\"data\":null");
    }

    @Test
    @DisplayName("未带 Token 访问不存在的路径：返回 401，不泄露路径是否存在")
    void should_not_leak_path_existence_when_anonymous() throws Exception {
        HttpResponse<String> response = get("/api/v1/definitely/not/exists", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":40101");
    }

    @Test
    @DisplayName("合法 Token：放行，且 LoginUser 正确注入（Account/权限回显）")
    void should_pass_with_valid_token_over_real_http() throws Exception {
        String token = tokenProvider.createToken(
                loginUser("operator", Set.of("sku:manage", "recommend:view")));

        HttpResponse<String> response = get("/api/v1/test/probe/whoami", token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"account\":\"operator\"");
        assertThat(response.body()).contains("sku:manage");
    }

    @Test
    @DisplayName("已认证访问不存在的路径：返回 404 与 code=40412（API_NOT_FOUND），不把 404 伪装成鉴权失败")
    void should_return_404_when_authenticated_and_path_absent() throws Exception {
        String token = tokenProvider.createToken(loginUser("admin", Set.of("user:manage")));

        HttpResponse<String> response = get("/api/v1/definitely/not/exists", token);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"code\":40412");
        assertThat(response.body()).contains("请求的接口不存在");
        assertThat(response.body()).contains("\"data\":null");
    }

    @Test
    @DisplayName("已登录但无权限：返回 403 与 code=40301（FR-RBAC-5）")
    void should_return_403_over_real_http() throws Exception {
        String token = tokenProvider.createToken(loginUser("viewer", Set.of("sim:view")));

        HttpResponse<String> response = get("/api/v1/test/probe/admin-only", token);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":40301");
        assertThat(response.body()).contains("无该操作权限");
    }

    @Test
    @DisplayName("Token 换密钥伪造：真实 HTTP 返回 401")
    void should_reject_forged_token_over_real_http() throws Exception {
        String forged = io.jsonwebtoken.Jwts.builder()
                .subject("hacker")
                .claim(JwtTokenProvider.CLAIM_USER_ID, 999L)
                .claim(JwtTokenProvider.CLAIM_PERMISSIONS, java.util.List.of("user:manage"))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                                "another-secret-key-long-enough-000000000000".getBytes(StandardCharsets.UTF_8)),
                        io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();

        HttpResponse<String> response = get("/api/v1/test/probe/admin-only", forged);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":40101");
    }

    /**
     * 探针 Controller：注册进真实应用，用于验证鉴权链路。
     */
    @RestController
    @RequestMapping("/api/v1/test/probe")
    static class ProbeController {

        /**
         * 回显当前登录用户（仅需登录）。
         *
         * <p>返回纯 JSON 字符串而非对象，避免测试对生产 Jackson 配置产生额外依赖。
         *
         * @return 当前登录信息
         */
        @GetMapping("/whoami")
        public String whoami() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            LoginUser user = (LoginUser) authentication.getPrincipal();
            String permissions = user.getPermissions().stream()
                    .map(code -> "\"" + code + "\"")
                    .collect(java.util.stream.Collectors.joining(","));
            return "{\"account\":\"" + user.getAccount()
                    + "\",\"name\":\"" + user.getName()
                    + "\",\"permissions\":[" + permissions + "]}";
        }

        /**
         * 仅 {@code user:manage} 可访问。
         *
         * @return 固定响应
         */
        @GetMapping("/admin-only")
        @PreAuthorize("hasAuthority('user:manage')")
        public String adminOnly() {
            return "{\"ok\":true}";
        }
    }
}
