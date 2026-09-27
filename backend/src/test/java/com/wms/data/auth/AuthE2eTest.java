package com.wms.data.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.WmsApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证模块真实 HTTP 端到端测试（a，A-B6）：以随机端口程序化启动**真实应用**
 * （真实 MySQL + Flyway + Spring Security + BCrypt），用 HTTP 验证 API-001 ~ API-005。
 *
 * <p>关键验证点：V3 种子数据中的 BCrypt 哈希能否被生产代码正确校验——
 * 这是「种子口令可用性」的最终证据（单元测试只能证明哈希本身合法）。
 *
 * <p>标记 {@code e2e}，执行：{@code mvn -s maven-settings.xml test -Pe2e -Dtest=AuthE2eTest}。
 * 依赖本机 MySQL（root/123456）与自动建库 {@code wms_sim_e2e}（Flyway 建表 + 种子数据）。
 *
 * @author a
 */
@Tag("e2e")
class AuthE2eTest {

    /** 与 V3__seed_rbac.sql 完全一致的 BCrypt 哈希（明文 admin123）。 */
    private static final List<String[]> SEED_ACCOUNTS = List.of(
            new String[]{"1", "admin", "系统管理员", "admin",
                    "$2a$10$hm2S8wL2GKbgWQbQBhBkf.ZPXY34Oj6ImINkoWmuGSNvw1H7DxfAq"},
            new String[]{"2", "operator", "仓库操作员", "operator",
                    "$2a$10$/kxN0KEhbX4ZdSGBlL22KehJ6FUuS3QeVd8w.lSXB8/dse9QpatXm"},
            new String[]{"3", "analyst", "分析人员", "analyst",
                    "$2a$10$nX1LxxcZ4XgxyI8vsr2JneMRq9GSJ8ljtSKlJK95qx47Cq8EwnDme"},
            new String[]{"4", "viewer", "只读访客", "viewer",
                    "$2a$10$cFWxpvD.PU9BIPgHg.kbFexrbYWriFENQnc5kolDblU/pvJkO6Zt."});

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static ConfigurableApplicationContext context;

    private static String baseUrl;

    private static JdbcTemplate jdbcTemplate;

    /**
     * 启动真实应用并准备好种子账号。
     */
    @BeforeAll
    static void startApplication() {
        // 用命令行参数（优先级最高）而不是 SpringApplicationBuilder.properties()：
        // 后者是「默认属性」，会被 application.yml 覆盖，导致用例连到真实库 wms_sim。
        context = new SpringApplicationBuilder(WmsApplication.class, AdminProbeController.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/wms_sim_e2e"
                                + "?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8"
                                + "&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                        "--spring.datasource.username=root",
                        "--spring.datasource.password=123456",
                        "--spring.flyway.enabled=true",
                        "--spring.flyway.baseline-on-migrate=true",
                        "--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl",
                        "--logging.level.root=WARN",
                        "--logging.level.com.wms=INFO");

        int port = context.getEnvironment().getProperty("local.server.port", Integer.class, 8080);
        baseUrl = "http://127.0.0.1:" + port;
        jdbcTemplate = context.getBean(JdbcTemplate.class);

        bootstrapSeedAccounts();
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
     * 保证四个种子账号存在且口令哈希与 V3 一致。
     *
     * <p>用 upsert 而非插入，是为了在账号被先前测试改动过时恢复原状
     * （角色与权限由 Flyway 的 V3 保证）。
     */
    private static void bootstrapSeedAccounts() {
        for (String[] account : SEED_ACCOUNTS) {
            jdbcTemplate.update(
                    "INSERT INTO users (id, account, password, name, status, remark) "
                            + "VALUES (?, ?, ?, ?, 'active', '认证模块 e2e 测试账号') "
                            + "ON DUPLICATE KEY UPDATE password = VALUES(password), status = 'active'",
                    Long.parseLong(account[0]), account[1], account[4], account[2]);
            jdbcTemplate.update(
                    "INSERT IGNORE INTO user_roles (user_id, role_id) "
                            + "SELECT ?, id FROM roles WHERE code = ?",
                    Long.parseLong(account[0]), account[3]);
        }
    }

    /**
     * 应用启动后可供同包/后续 e2e 测试复用的上下文（当前类自带实例，保留访问器便于扩展）。
     *
     * @return Spring 上下文
     */
    static ConfigurableApplicationContext applicationContext() {
        return context;
    }

    /**
     * 共享 HTTP 客户端。
     *
     * @return HttpClient
     */
    static HttpClient httpClient() {
        return HTTP;
    }

    /**
     * 发送请求。
     *
     * @param method HTTP 方法
     * @param path   路径
     * @param body   请求体 JSON，可为 null
     * @param token  JWT，可为 null
     * @return HTTP 响应
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private HttpResponse<String> send(String method, String path, String body, String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        builder.method(method, publisher);
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 登录并返回响应。
     *
     * @param account  账号
     * @param password 密码
     * @return HTTP 响应
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private HttpResponse<String> login(String account, String password) throws IOException, InterruptedException {
        return send("POST", "/api/v1/auth/login",
                "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}", null);
    }

    /**
     * 登录并取回 Token。
     *
     * @param account  账号
     * @param password 密码
     * @return JWT
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private String loginForToken(String account, String password) throws IOException, InterruptedException {
        HttpResponse<String> response = login(account, password);
        assertThat(response.statusCode()).as("登录 %s 应成功", account).isEqualTo(200);
        return MAPPER.readTree(response.body()).get("data").get("token").asText();
    }

    @Test
    @DisplayName("四个种子账号用 admin123 均可登录成功，且返回的角色与权限与《接口文档》1.4 完全一致")
    void should_login_all_seed_accounts_and_match_permission_matrix() throws Exception {
        JsonNode admin = MAPPER.readTree(login("admin", "admin123").body()).get("data");
        assertThat(admin.get("token").asText()).isNotBlank();
        assertThat(admin.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(toList(admin.get("user").get("roles"))).containsExactly("admin");
        assertThat(toList(admin.get("user").get("permissions")))
                .containsExactlyInAnyOrder("user:manage", "warehouse:manage", "sku:manage",
                        "recommend:view", "sim:run", "sim:view", "compare:view", "report:export",
                        "config:manage");

        JsonNode operator = MAPPER.readTree(login("operator", "admin123").body()).get("data");
        assertThat(toList(operator.get("user").get("roles"))).containsExactly("operator");
        assertThat(toList(operator.get("user").get("permissions")))
                .containsExactlyInAnyOrder("sku:manage", "recommend:view", "sim:run", "sim:view");

        JsonNode analyst = MAPPER.readTree(login("analyst", "admin123").body()).get("data");
        assertThat(toList(analyst.get("user").get("roles"))).containsExactly("analyst");
        assertThat(toList(analyst.get("user").get("permissions")))
                .containsExactlyInAnyOrder("sim:run", "sim:view", "compare:view", "report:export");

        JsonNode viewer = MAPPER.readTree(login("viewer", "admin123").body()).get("data");
        assertThat(toList(viewer.get("user").get("roles"))).containsExactly("viewer");
        assertThat(toList(viewer.get("user").get("permissions")))
                .containsExactlyInAnyOrder("sim:view", "compare:view");
    }

    @Test
    @DisplayName("登录响应绝不包含密码字段（NFR-6）")
    void should_never_return_password() throws Exception {
        HttpResponse<String> response = login("admin", "admin123");

        assertThat(response.body()).doesNotContain("password");
        assertThat(response.body()).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("密码错误与账号不存在返回同一错误码 40102，避免账号枚举")
    void should_return_same_error_for_wrong_password_and_unknown_account() throws Exception {
        HttpResponse<String> wrongPassword = login("admin", "wrong-password");
        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(MAPPER.readTree(wrongPassword.body()).get("code").asInt()).isEqualTo(40102);

        HttpResponse<String> unknownAccount = login("no-such-user", "admin123");
        assertThat(unknownAccount.statusCode()).isEqualTo(401);
        assertThat(MAPPER.readTree(unknownAccount.body()).get("code").asInt()).isEqualTo(40102);
        assertThat(unknownAccount.body()).isEqualTo(wrongPassword.body());
    }

    @Test
    @DisplayName("账号被禁用时登录返回 40103")
    void should_reject_disabled_account() throws Exception {
        jdbcTemplate.update("UPDATE users SET status = 'disabled' WHERE account = 'viewer'");
        try {
            HttpResponse<String> response = login("viewer", "admin123");

            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(MAPPER.readTree(response.body()).get("code").asInt()).isEqualTo(40103);
        } finally {
            jdbcTemplate.update("UPDATE users SET status = 'active' WHERE account = 'viewer'");
        }
    }

    @Test
    @DisplayName("未登录访问 /auth/me 返回 401；携带 Token 则返回本人信息与权限")
    void should_require_token_for_me_and_return_profile() throws Exception {
        assertThat(send("GET", "/api/v1/auth/me", null, null).statusCode()).isEqualTo(401);

        String token = loginForToken("operator", "admin123");
        HttpResponse<String> response = send("GET", "/api/v1/auth/me", null, token);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(response.body()).get("data");
        assertThat(data.get("account").asText()).isEqualTo("operator");
        assertThat(data.get("name").asText()).isEqualTo("仓库操作员");
        assertThat(toList(data.get("roles"))).containsExactly("operator");
        assertThat(toList(data.get("permissions"))).contains("sku:manage");
        assertThat(data.has("password")).isFalse();
    }

    @Test
    @DisplayName("API-005：admin 可创建用户并绑定角色；operator 调用被 403 拦截")
    void should_create_user_with_admin_and_forbid_operator() throws Exception {
        String adminToken = loginForToken("admin", "admin123");
        String operatorToken = loginForToken("operator", "admin123");
        String account = "e2e_user_" + System.nanoTime() % 100000;

        HttpResponse<String> forbidden = send("POST", "/api/v1/users",
                "{\"account\":\"" + account + "\",\"password\":\"pass123456\",\"roles\":[\"analyst\"]}",
                operatorToken);
        assertThat(forbidden.statusCode()).isEqualTo(403);
        assertThat(MAPPER.readTree(forbidden.body()).get("code").asInt()).isEqualTo(40301);
        assertThat(countUser(account)).isZero();

        HttpResponse<String> created = send("POST", "/api/v1/users",
                "{\"account\":\"" + account + "\",\"password\":\"pass123456\",\"name\":\"端到端用户\","
                        + "\"roles\":[\"analyst\"],\"email\":\"e2e@wms.local\"}",
                adminToken);
        assertThat(created.statusCode()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(created.body()).get("data");
        assertThat(data.get("account").asText()).isEqualTo(account);
        assertThat(toList(data.get("roles"))).containsExactly("analyst");
        assertThat(toList(data.get("permissions"))).contains("report:export");
        assertThat(data.get("status").asText()).isEqualTo("active");

        try {
            String newUserToken = loginForToken(account, "pass123456");
            assertThat(send("GET", "/api/v1/auth/me", null, newUserToken).statusCode()).isEqualTo(200);
        } finally {
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN "
                    + "(SELECT id FROM (SELECT id FROM users WHERE account = ?) t)", account);
            jdbcTemplate.update("DELETE FROM users WHERE account = ?", account);
        }
    }

    @Test
    @DisplayName("API-005：账号重复返回 42207；角色不存在返回 40408；缺参数返回 40001")
    void should_reject_invalid_create_user_requests() throws Exception {
        String adminToken = loginForToken("admin", "admin123");

        HttpResponse<String> duplicate = send("POST", "/api/v1/users",
                "{\"account\":\"admin\",\"password\":\"pass123456\"}", adminToken);
        assertThat(duplicate.statusCode()).isEqualTo(422);
        assertThat(MAPPER.readTree(duplicate.body()).get("code").asInt()).isEqualTo(42207);

        HttpResponse<String> badRole = send("POST", "/api/v1/users",
                "{\"account\":\"e2e_badrole\",\"password\":\"pass123456\",\"roles\":[\"no-such-role\"]}",
                adminToken);
        assertThat(badRole.statusCode()).isEqualTo(404);
        assertThat(MAPPER.readTree(badRole.body()).get("code").asInt()).isEqualTo(40408);

        HttpResponse<String> missingField = send("POST", "/api/v1/users",
                "{\"account\":\"\",\"password\":\"pass123456\"}", adminToken);
        assertThat(missingField.statusCode()).isEqualTo(400);
        assertThat(MAPPER.readTree(missingField.body()).get("code").asInt()).isEqualTo(40001);
        assertThat(MAPPER.readTree(missingField.body()).get("message").asText()).contains("账号不能为空");
    }

    @Test
    @DisplayName("API-004：改密需校验原密码；改密后旧密码失效、新密码可登录")
    void should_change_password_and_invalidate_old_one() throws Exception {
        String account = "e2e_pwd_" + System.nanoTime() % 100000;
        String adminToken = loginForToken("admin", "admin123");
        send("POST", "/api/v1/users",
                "{\"account\":\"" + account + "\",\"password\":\"oldpass123\",\"roles\":[\"viewer\"]}",
                adminToken);

        try {
            String token = loginForToken(account, "oldpass123");

            HttpResponse<String> wrongOld = send("PUT", "/api/v1/auth/password",
                    "{\"oldPassword\":\"not-the-old-one\",\"newPassword\":\"newpass456\"}", token);
            assertThat(wrongOld.statusCode()).isEqualTo(401);
            assertThat(MAPPER.readTree(wrongOld.body()).get("code").asInt()).isEqualTo(40104);

            HttpResponse<String> changed = send("PUT", "/api/v1/auth/password",
                    "{\"oldPassword\":\"oldpass123\",\"newPassword\":\"newpass456\"}", token);
            assertThat(changed.statusCode()).isEqualTo(200);
            assertThat(MAPPER.readTree(changed.body()).get("code").asInt()).isZero();

            assertThat(login(account, "oldpass123").statusCode()).isEqualTo(401);
            assertThat(login(account, "newpass456").statusCode()).isEqualTo(200);
        } finally {
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id IN "
                    + "(SELECT id FROM (SELECT id FROM users WHERE account = ?) t)", account);
            jdbcTemplate.update("DELETE FROM users WHERE account = ?", account);
        }
    }

    @Test
    @DisplayName("API-002：登出返回成功；登出后原 Token 仍有效（无状态 JWT 的已声明取舍）")
    void should_logout_and_document_stateless_behaviour() throws Exception {
        String token = loginForToken("viewer", "admin123");

        HttpResponse<String> logout = send("POST", "/api/v1/auth/logout", null, token);
        assertThat(logout.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(logout.body()).get("code").asInt()).isZero();

        // 无状态方案的既有取舍：Token 在有效期内继续可用（《鉴权中间件规范》2.4）
        assertThat(send("GET", "/api/v1/auth/me", null, token).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("登录成功会写入审计日志（《代码规范》4.6）")
    void should_write_audit_log_on_login() throws Exception {
        loginForToken("analyst", "admin123");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE account = 'analyst' AND action = 'login'",
                Integer.class);
        assertThat(count).isNotNull().isGreaterThan(0);
    }

    /**
     * JSON 数组转字符串列表。
     *
     * @param node JSON 数组节点
     * @return 字符串列表
     */
    private List<String> toList(JsonNode node) {
        assertThat(node).isNotNull();
        assertThat(node.isArray()).isTrue();
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    /**
     * 统计某账号的用户数。
     *
     * @param account 账号
     * @return 数量
     */
    private int countUser(String account) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE account = ?", Integer.class, account);
        return count == null ? 0 : count;
    }

    /**
     * 仅用于验证越权拦截的探针接口（需要 {@code user:manage}）。
     */
    @RestController
    @RequestMapping("/api/v1/test/admin-probe")
    static class AdminProbeController {

        /**
         * 需要 {@code user:manage} 权限。
         *
         * @return 固定响应
         */
        @GetMapping
        @PreAuthorize("hasAuthority('user:manage')")
        public String adminOnly() {
            return "{\"ok\":true}";
        }
    }
}
