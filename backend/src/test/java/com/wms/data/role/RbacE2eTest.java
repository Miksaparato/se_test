package com.wms.data.role;

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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC 管理真实 HTTP 端到端测试（a，A-B7）：以随机端口启动**真实应用**
 * （真实 MySQL + Flyway + Spring Security），验证 API-006 ~ API-015。
 *
 * <p>覆盖 FR-RBAC-2（角色增删与权限分配）、FR-RBAC-4（内置四角色）与 FR-RBAC-5（越权拦截），
 * 并验证「改权限 → 重新登录 → 权限生效」这一端到端闭环。
 *
 * <p>标记 {@code e2e}，执行：{@code mvn -s maven-settings.xml test -Pe2e -Dtest=RbacE2eTest}。
 * 使用独立数据库 {@code wms_sim_e2e_rbac}，避免与其它用例互相污染。
 *
 * @author a
 */
@Tag("e2e")
class RbacE2eTest {

    /** 四个账号的 id、账号、姓名、角色、与 V3 一致的 BCrypt 哈希（明文 admin123）。 */
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

    /** 本测试期间创建的自定义角色 id，用于清理。 */
    private static final List<Long> CREATED_ROLE_IDS = new ArrayList<>();

    /**
     * 启动真实应用并准备种子数据。
     */
    @BeforeAll
    static void startApplication() {
        // 用命令行参数（优先级最高）：SpringApplicationBuilder.properties() 是默认属性，
        // 会被 application.yml 覆盖而连到真实库 wms_sim。
        context = new SpringApplicationBuilder(WmsApplication.class, AdminProbeController.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/wms_sim_e2e_rbac"
                                + "?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8"
                                + "&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                        "--spring.datasource.username=root",
                        "--spring.datasource.password=123456",
                        "--spring.flyway.enabled=true",
                        "--mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl",
                        "--logging.level.root=WARN");

        baseUrl = "http://127.0.0.1:"
                + context.getEnvironment().getProperty("local.server.port", Integer.class, 8081);
        jdbcTemplate = context.getBean(JdbcTemplate.class);
        bootstrapSeedAccounts();
    }

    /**
     * 关闭应用并清理本测试创建的角色。
     */
    @AfterAll
    static void stopApplication() {
        if (jdbcTemplate != null) {
            for (Long roleId : CREATED_ROLE_IDS) {
                jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
                jdbcTemplate.update("DELETE FROM user_roles WHERE role_id = ?", roleId);
                jdbcTemplate.update("DELETE FROM roles WHERE id = ?", roleId);
            }
        }
        if (context != null) {
            context.close();
        }
    }

    /**
     * 保证四个种子账号存在、状态启用且角色绑定正确；同时恢复内置角色的默认权限。
     */
    private static void bootstrapSeedAccounts() {
        for (String[] account : SEED_ACCOUNTS) {
            jdbcTemplate.update(
                    "INSERT INTO users (id, account, password, name, status, remark) "
                            + "VALUES (?, ?, ?, ?, 'active', 'RBAC e2e 测试账号') "
                            + "ON DUPLICATE KEY UPDATE password = VALUES(password), status = 'active'",
                    Long.parseLong(account[0]), account[1], account[4], account[2]);
            jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", Long.parseLong(account[0]));
            jdbcTemplate.update("INSERT IGNORE INTO user_roles (user_id, role_id) "
                    + "SELECT ?, id FROM roles WHERE code = ?", Long.parseLong(account[0]), account[3]);
        }
        // 恢复 viewer 的内置默认权限（sim:view、compare:view），避免上一轮用例残留
        jdbcTemplate.update("DELETE FROM role_permissions WHERE role_id = "
                + "(SELECT id FROM (SELECT id FROM roles WHERE code = 'viewer') t)");
        jdbcTemplate.update("INSERT INTO role_permissions (role_id, permission_id) "
                + "SELECT r.id, p.id FROM roles r JOIN permissions p "
                + "WHERE r.code = 'viewer' AND p.code IN ('sim:view', 'compare:view')");
        // admin 保持全部权限
        jdbcTemplate.update("INSERT IGNORE INTO role_permissions (role_id, permission_id) "
                + "SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'admin'");
    }

    /**
     * 发送 HTTP 请求。
     *
     * @param method HTTP 方法
     * @param path   路径
     * @param body   请求体，可为 null
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
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
    private String login(String account, String password) throws IOException, InterruptedException {
        HttpResponse<String> response = send("POST", "/api/v1/auth/login",
                "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}", null);
        assertThat(response.statusCode()).as("登录 %s 应成功，实际 %s", account, response.body()).isEqualTo(200);
        return MAPPER.readTree(response.body()).get("data").get("token").asText();
    }

    /**
     * 读取统一响应中的 code。
     *
     * @param response HTTP 响应
     * @return 业务错误码
     * @throws IOException JSON 解析失败
     */
    private int codeOf(HttpResponse<String> response) throws IOException {
        return MAPPER.readTree(response.body()).get("code").asInt();
    }

    /**
     * JSON 数组转字符串列表。
     *
     * @param node JSON 节点
     * @return 字符串列表
     */
    private List<String> toList(JsonNode node) {
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    // ---------------------------------------------------------------- API-006 ~ 009

    @Test
    @DisplayName("API-006：admin 可分页查询用户，列表含角色且不含密码")
    void should_list_users_with_roles_and_without_password() throws Exception {
        String token = login("admin", "admin123");

        HttpResponse<String> response = send("GET", "/api/v1/users?page=1&page_size=10", null, token);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(response.body()).get("data");
        assertThat(data.get("page").asInt()).isEqualTo(1);
        assertThat(data.get("page_size").asInt()).isEqualTo(10);
        assertThat(data.get("total").asLong()).isGreaterThanOrEqualTo(4);
        assertThat(response.body()).doesNotContain("password");

        JsonNode admin = null;
        for (JsonNode item : data.get("list")) {
            if ("admin".equals(item.get("account").asText())) {
                admin = item;
            }
        }
        assertThat(admin).as("列表应包含内置 admin 账号").isNotNull();
        assertThat(toList(admin.get("roles"))).containsExactly("admin");
        assertThat(admin.get("status").asText()).isEqualTo("active");
    }

    @Test
    @DisplayName("API-006：按状态与关键字过滤生效")
    void should_filter_users_by_status_and_keyword() throws Exception {
        String token = login("admin", "admin123");

        HttpResponse<String> byKeyword = send("GET", "/api/v1/users?keyword=oper", null, token);
        JsonNode list = MAPPER.readTree(byKeyword.body()).get("data").get("list");
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).get("account").asText()).isEqualTo("operator");

        HttpResponse<String> byStatus = send("GET", "/api/v1/users?status=disabled", null, token);
        assertThat(MAPPER.readTree(byStatus.body()).get("data").get("total").asLong()).isZero();
    }

    @Test
    @DisplayName("API-006：page_size 超上限被截断为 100，非法页码归为 1")
    void should_clamp_pagination_parameters() throws Exception {
        String token = login("admin", "admin123");

        HttpResponse<String> response = send("GET", "/api/v1/users?page=0&page_size=5000", null, token);

        JsonNode data = MAPPER.readTree(response.body()).get("data");
        assertThat(data.get("page").asInt()).isEqualTo(1);
        assertThat(data.get("page_size").asInt()).isEqualTo(100);
    }

    @Test
    @DisplayName("API-007：更新姓名与角色生效；角色不存在返回 40408")
    void should_update_user_and_reject_unknown_role() throws Exception {
        String adminToken = login("admin", "admin123");
        Long userId = createUser(adminToken, "e2e_upd", "pass123456", "[\"viewer\"]");

        HttpResponse<String> updated = send("PUT", "/api/v1/users/" + userId,
                "{\"name\":\"改名后\",\"email\":\"new@wms.local\",\"roles\":[\"analyst\"]}", adminToken);
        assertThat(updated.statusCode()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(updated.body()).get("data");
        assertThat(data.get("name").asText()).isEqualTo("改名后");
        assertThat(data.get("email").asText()).isEqualTo("new@wms.local");
        assertThat(toList(data.get("roles"))).containsExactly("analyst");
        assertThat(toList(data.get("permissions"))).contains("report:export");

        HttpResponse<String> badRole = send("PUT", "/api/v1/users/" + userId,
                "{\"roles\":[\"ghost-role\"]}", adminToken);
        assertThat(badRole.statusCode()).isEqualTo(404);
        assertThat(codeOf(badRole)).isEqualTo(40408);

        HttpResponse<String> notFound = send("PUT", "/api/v1/users/99999999", "{\"name\":\"x\"}", adminToken);
        assertThat(notFound.statusCode()).isEqualTo(404);
        assertThat(codeOf(notFound)).isEqualTo(40407);

        deleteUserQuietly(userId);
    }

    @Test
    @DisplayName("API-008：禁用用户后其无法登录；启用后恢复；禁止禁用当前登录账号")
    void should_disable_user_and_block_self_disable() throws Exception {
        String adminToken = login("admin", "admin123");
        Long userId = createUser(adminToken, "e2e_status", "pass123456", "[\"viewer\"]");

        try {
            assertThat(send("POST", "/api/v1/auth/login",
                    "{\"account\":\"e2e_status\",\"password\":\"pass123456\"}", null).statusCode()).isEqualTo(200);

            HttpResponse<String> disabled = send("PUT", "/api/v1/users/" + userId + "/status",
                    "{\"status\":\"disabled\"}", adminToken);
            assertThat(disabled.statusCode()).isEqualTo(200);
            assertThat(MAPPER.readTree(disabled.body()).get("data").get("status").asText()).isEqualTo("disabled");

            HttpResponse<String> loginDenied = send("POST", "/api/v1/auth/login",
                    "{\"account\":\"e2e_status\",\"password\":\"pass123456\"}", null);
            assertThat(loginDenied.statusCode()).isEqualTo(401);
            assertThat(codeOf(loginDenied)).isEqualTo(40103);

            HttpResponse<String> enabled = send("PUT", "/api/v1/users/" + userId + "/status",
                    "{\"status\":\"active\"}", adminToken);
            assertThat(enabled.statusCode()).isEqualTo(200);
            assertThat(send("POST", "/api/v1/auth/login",
                    "{\"account\":\"e2e_status\",\"password\":\"pass123456\"}", null).statusCode()).isEqualTo(200);

            HttpResponse<String> selfDisable = send("PUT", "/api/v1/users/1/status",
                    "{\"status\":\"disabled\"}", adminToken);
            assertThat(selfDisable.statusCode()).isEqualTo(422);
            assertThat(codeOf(selfDisable)).isEqualTo(42215);

            HttpResponse<String> badStatus = send("PUT", "/api/v1/users/" + userId + "/status",
                    "{\"status\":\"sleeping\"}", adminToken);
            assertThat(badStatus.statusCode()).isEqualTo(400);
            assertThat(codeOf(badStatus)).isEqualTo(40001);
        } finally {
            deleteUserQuietly(userId);
        }
    }

    @Test
    @DisplayName("API-009：删除用户会清理角色关联；保留账号与自身账号不可删除")
    void should_delete_user_and_protect_reserved_and_self() throws Exception {
        String adminToken = login("admin", "admin123");
        Long userId = createUser(adminToken, "e2e_del", "pass123456", "[\"viewer\"]");
        assertThat(countUserRoles(userId)).isEqualTo(1);

        HttpResponse<String> deleted = send("DELETE", "/api/v1/users/" + userId, null, adminToken);
        assertThat(deleted.statusCode()).isEqualTo(200);
        assertThat(countUsers(userId)).isZero();
        assertThat(countUserRoles(userId)).isZero();

        HttpResponse<String> deleteSelf = send("DELETE", "/api/v1/users/1", null, adminToken);
        assertThat(deleteSelf.statusCode()).isEqualTo(422);
        assertThat(codeOf(deleteSelf)).isEqualTo(42215);

        HttpResponse<String> deleteMissing = send("DELETE", "/api/v1/users/99999999", null, adminToken);
        assertThat(deleteMissing.statusCode()).isEqualTo(404);
        assertThat(codeOf(deleteMissing)).isEqualTo(40407);

        // 保留账号保护规则：其他管理员删除 admin 账号时被拒（42216）
        Long tempAdminId = createUser(adminToken, "e2e_admin2", "pass123456", "[\"admin\"]");
        assertThat(tempAdminId).isNotNull();
        try {
            String tempAdminToken = login("e2e_admin2", "pass123456");
            HttpResponse<String> deleteReserved = send("DELETE", "/api/v1/users/1", null, tempAdminToken);
            assertThat(deleteReserved.statusCode()).isEqualTo(422);
            assertThat(codeOf(deleteReserved)).isEqualTo(42216);
            assertThat(countUsers(1L)).isEqualTo(1);
        } finally {
            deleteUserQuietly(tempAdminId);
        }
    }

    // ---------------------------------------------------------------- API-010 ~ 015

    @Test
    @DisplayName("API-010：角色列表含内置四角色，并带权限码与用户数")
    void should_list_builtin_roles_with_permissions_and_user_count() throws Exception {
        String token = login("admin", "admin123");

        HttpResponse<String> response = send("GET", "/api/v1/roles", null, token);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode list = MAPPER.readTree(response.body()).get("data");
        assertThat(list.size()).isGreaterThanOrEqualTo(4);

        JsonNode viewer = null;
        JsonNode admin = null;
        for (JsonNode role : list) {
            if ("viewer".equals(role.get("code").asText())) {
                viewer = role;
            }
            if ("admin".equals(role.get("code").asText())) {
                admin = role;
            }
        }
        assertThat(viewer).isNotNull();
        assertThat(viewer.get("isBuiltin").asBoolean()).isTrue();
        assertThat(toList(viewer.get("permissions"))).containsExactlyInAnyOrder("sim:view", "compare:view");
        assertThat(admin).isNotNull();
        assertThat(admin.get("userCount").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(toList(admin.get("permissions"))).hasSize(9);
    }

    @Test
    @DisplayName("API-011：创建角色成功；角色标识重复返回 42208；格式非法返回 40001")
    void should_create_role_and_reject_duplicate_or_invalid_code() throws Exception {
        String token = login("admin", "admin123");
        String code = "e2e_role_" + System.nanoTime() % 100000;

        HttpResponse<String> created = send("POST", "/api/v1/roles",
                "{\"code\":\"" + code + "\",\"name\":\"端到端角色\",\"description\":\"测试用\"}", token);
        assertThat(created.statusCode()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(created.body()).get("data");
        assertThat(data.get("code").asText()).isEqualTo(code);
        assertThat(data.get("isBuiltin").asBoolean()).isFalse();
        assertThat(data.get("userCount").asLong()).isZero();
        CREATED_ROLE_IDS.add(data.get("id").asLong());

        HttpResponse<String> duplicate = send("POST", "/api/v1/roles",
                "{\"code\":\"" + code + "\",\"name\":\"重复\"}", token);
        assertThat(duplicate.statusCode()).isEqualTo(422);
        assertThat(codeOf(duplicate)).isEqualTo(42208);

        HttpResponse<String> builtinDuplicate = send("POST", "/api/v1/roles",
                "{\"code\":\"admin\",\"name\":\"假管理员\"}", token);
        assertThat(codeOf(builtinDuplicate)).isEqualTo(42208);

        HttpResponse<String> invalid = send("POST", "/api/v1/roles",
                "{\"code\":\"Bad Code\",\"name\":\"非法\"}", token);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(codeOf(invalid)).isEqualTo(40001);
    }

    @Test
    @DisplayName("API-012：更新角色名称/描述生效；角色不存在返回 40408")
    void should_update_role() throws Exception {
        String token = login("admin", "admin123");
        String code = "e2e_upd_role_" + System.nanoTime() % 100000;
        Long roleId = createRole(token, code, "原名");

        HttpResponse<String> updated = send("PUT", "/api/v1/roles/" + roleId,
                "{\"name\":\"新名\",\"description\":\"新描述\"}", token);
        assertThat(updated.statusCode()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(updated.body()).get("data");
        assertThat(data.get("name").asText()).isEqualTo("新名");
        assertThat(data.get("description").asText()).isEqualTo("新描述");
        assertThat(data.get("code").asText()).isEqualTo(code);

        HttpResponse<String> missing = send("PUT", "/api/v1/roles/99999999", "{\"name\":\"x\"}", token);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(codeOf(missing)).isEqualTo(40408);
    }

    @Test
    @DisplayName("API-013：内置角色返回 40302；仍被用户引用的角色返回 42214；自定义空角色可删除")
    void should_protect_builtin_and_in_use_roles_on_delete() throws Exception {
        String token = login("admin", "admin123");

        HttpResponse<String> builtin = send("DELETE", "/api/v1/roles/1", null, token);
        assertThat(builtin.statusCode()).isEqualTo(403);
        assertThat(codeOf(builtin)).isEqualTo(40302);
        assertThat(countRoles(1L)).isEqualTo(1);

        String code = "e2e_inuse_" + System.nanoTime() % 100000;
        Long roleId = createRole(token, code, "被引用角色");
        Long userId = createUser(token, "e2e_inuse_user", "pass123456", "[\"" + code + "\"]");
        assertThat(userId).isNotNull();

        try {
            HttpResponse<String> inUse = send("DELETE", "/api/v1/roles/" + roleId, null, token);
            assertThat(inUse.statusCode()).isEqualTo(422);
            assertThat(codeOf(inUse)).isEqualTo(42214);
            assertThat(MAPPER.readTree(inUse.body()).get("message").asText()).contains("1 个用户");
            assertThat(countRoles(roleId)).isEqualTo(1);
        } finally {
            deleteUserQuietly(userId);
        }

        HttpResponse<String> deleted = send("DELETE", "/api/v1/roles/" + roleId, null, token);
        assertThat(deleted.statusCode()).isEqualTo(200);
        assertThat(countRoles(roleId)).isZero();
        CREATED_ROLE_IDS.remove(roleId);
    }

    @Test
    @DisplayName("API-014：分配权限全量覆盖；非法权限码返回 42212；空数组收回全部权限")
    void should_assign_permissions_with_full_overwrite() throws Exception {
        String token = login("admin", "admin123");
        String code = "e2e_perm_" + System.nanoTime() % 100000;
        Long roleId = createRole(token, code, "权限测试角色");
        Long viewerRoleId = roleIdOf("viewer");

        HttpResponse<String> assigned = send("PUT", "/api/v1/roles/" + roleId + "/permissions",
                "{\"permissions\":[\"sim:view\",\"sim:run\",\"report:export\"]}", token);
        assertThat(assigned.statusCode()).isEqualTo(200);
        assertThat(toList(MAPPER.readTree(assigned.body()).get("data").get("permissions")))
                .containsExactlyInAnyOrder("sim:view", "sim:run", "report:export");

        HttpResponse<String> overwritten = send("PUT", "/api/v1/roles/" + roleId + "/permissions",
                "{\"permissions\":[\"sim:view\"]}", token);
        assertThat(toList(MAPPER.readTree(overwritten.body()).get("data").get("permissions")))
                .containsExactly("sim:view");

        HttpResponse<String> invalid = send("PUT", "/api/v1/roles/" + roleId + "/permissions",
                "{\"permissions\":[\"sim:view\",\"super:power\"]}", token);
        assertThat(invalid.statusCode()).isEqualTo(422);
        assertThat(codeOf(invalid)).isEqualTo(42212);
        assertThat(MAPPER.readTree(invalid.body()).get("message").asText()).contains("super:power");
        assertThat(toList(readPermissions(roleId, token))).containsExactly("sim:view");

        HttpResponse<String> nullBody = send("PUT", "/api/v1/roles/" + roleId + "/permissions",
                "{}", token);
        assertThat(nullBody.statusCode()).isEqualTo(400);
        assertThat(codeOf(nullBody)).isEqualTo(40001);

        HttpResponse<String> cleared = send("PUT", "/api/v1/roles/" + roleId + "/permissions",
                "{\"permissions\":[]}", token);
        assertThat(cleared.statusCode()).isEqualTo(200);
        assertThat(toList(MAPPER.readTree(cleared.body()).get("data").get("permissions"))).isEmpty();

        HttpResponse<String> missingRole = send("PUT", "/api/v1/roles/99999999/permissions",
                "{\"permissions\":[\"sim:view\"]}", token);
        assertThat(missingRole.statusCode()).isEqualTo(404);
        assertThat(codeOf(missingRole)).isEqualTo(40408);

        // 恢复 viewer 默认权限，避免影响其它用例
        send("PUT", "/api/v1/roles/" + viewerRoleId + "/permissions",
                "{\"permissions\":[\"sim:view\",\"compare:view\"]}", token);
    }

    @Test
    @DisplayName("API-014 → 重新登录：viewer 被授予 sim:run 后可运行仿真，收回后恢复 403（端到端闭环）")
    void should_make_permission_change_effective_after_relogin() throws Exception {
        String adminToken = login("admin", "admin123");
        Long viewerRoleId = roleIdOf("viewer");

        try {
            // 初始：viewer 无 sim:run，探针接口应 403
            String viewerToken = login("viewer", "admin123");
            assertThat(send("GET", "/api/v1/test/admin-probe/run-probe", null, viewerToken).statusCode())
                    .isEqualTo(403);

            // 授予 sim:run
            HttpResponse<String> granted = send("PUT", "/api/v1/roles/" + viewerRoleId + "/permissions",
                    "{\"permissions\":[\"sim:view\",\"compare:view\",\"sim:run\"]}", adminToken);
            assertThat(granted.statusCode()).isEqualTo(200);

            // 旧 Token 仍带旧权限（已声明的取舍）：仍 403
            assertThat(send("GET", "/api/v1/test/admin-probe/run-probe", null, viewerToken).statusCode())
                    .isEqualTo(403);

            // 重新登录后生效：200
            String refreshedToken = login("viewer", "admin123");
            HttpResponse<String> allowed = send("GET", "/api/v1/test/admin-probe/run-probe", null, refreshedToken);
            assertThat(allowed.statusCode()).isEqualTo(200);
            assertThat(allowed.body()).contains("ok");

            // 收回权限 → 再次登录后恢复 403
            send("PUT", "/api/v1/roles/" + viewerRoleId + "/permissions",
                    "{\"permissions\":[\"sim:view\",\"compare:view\"]}", adminToken);
            String revokedToken = login("viewer", "admin123");
            assertThat(send("GET", "/api/v1/test/admin-probe/run-probe", null, revokedToken).statusCode())
                    .isEqualTo(403);
        } finally {
            send("PUT", "/api/v1/roles/" + viewerRoleId + "/permissions",
                    "{\"permissions\":[\"sim:view\",\"compare:view\"]}", adminToken);
        }
    }

    @Test
    @DisplayName("API-015：权限清单返回 9 项，标识与《接口文档》1.4 完全一致")
    void should_list_permission_catalog_matching_interface_doc() throws Exception {
        String token = login("admin", "admin123");

        HttpResponse<String> response = send("GET", "/api/v1/permissions", null, token);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode list = MAPPER.readTree(response.body()).get("data");
        assertThat(list.size()).isEqualTo(9);

        List<String> codes = new ArrayList<>();
        List<String> types = new ArrayList<>();
        for (JsonNode item : list) {
            codes.add(item.get("code").asText());
            types.add(item.get("type").asText());
            assertThat(item.get("name").asText()).isNotBlank();
            assertThat(item.get("sortNo").asInt()).isGreaterThan(0);
        }
        assertThat(codes).containsExactlyInAnyOrder("user:manage", "warehouse:manage", "sku:manage",
                "recommend:view", "sim:run", "sim:view", "compare:view", "report:export", "config:manage");
        assertThat(types).containsOnly("menu", "action");
    }

    @Test
    @DisplayName("RBAC 管理接口对非 admin 角色一律 403（含 operator 与 viewer）")
    void should_forbid_all_rbac_endpoints_for_non_admin() throws Exception {
        String operatorToken = login("operator", "admin123");
        String analystToken = login("analyst", "admin123");
        String viewerToken = login("viewer", "admin123");

        String[][] endpoints = {
                {"DELETE", "/api/v1/users/1"},
                {"POST", "/api/v1/roles"},
                {"PUT", "/api/v1/roles/1"},
                {"DELETE", "/api/v1/roles/2"},
                {"PUT", "/api/v1/roles/1/permissions"},
                {"PUT", "/api/v1/users/1/status"}
        };
        for (String token : List.of(operatorToken, analystToken, viewerToken)) {
            for (String[] endpoint : endpoints) {
                HttpResponse<String> response = send(endpoint[0], endpoint[1],
                        "PUT".equals(endpoint[0]) || "POST".equals(endpoint[0]) ? "{}" : null, token);
                assertThat(response.statusCode())
                        .as("%s %s 应被 403 拦截，实际 %d 响应体=%s",
                                endpoint[0], endpoint[1], response.statusCode(), response.body())
                        .isEqualTo(403);
                assertThat(codeOf(response)).isEqualTo(40301);
            }
        }

        /*
         * 带非法参数的越权请求同样必须 403（而不是先触发 @Valid 返回 400）。
         * 这依赖 SecurityConfig 中的 URL 级兜底规则：@PreAuthorize 在 @Valid 之后执行，
         * 单靠方法级注解无法拦住这类请求。
         */
        String[][] invalidBodyEndpoints = {
                {"POST", "/api/v1/users", "{}"},
                {"PUT", "/api/v1/users/1", "{}"},
                {"POST", "/api/v1/roles", "{}"},
                {"PUT", "/api/v1/roles/1/permissions", "{}"}
        };
        for (String token : List.of(operatorToken, viewerToken)) {
            for (String[] endpoint : invalidBodyEndpoints) {
                HttpResponse<String> response = send(endpoint[0], endpoint[1], endpoint[2], token);
                assertThat(response.statusCode())
                        .as("带非法参数的 %s %s 也必须 403，实际 %d 响应体=%s",
                                endpoint[0], endpoint[1], response.statusCode(), response.body())
                        .isEqualTo(403);
                assertThat(codeOf(response)).isEqualTo(40301);
            }
        }

        HttpResponse<String> anonymous = send("GET", "/api/v1/roles", null, null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(codeOf(anonymous)).isEqualTo(40101);
    }

    @Test
    @DisplayName("越权尝试不会产生任何副作用：operator 调用创建角色后角色数不变")
    void should_not_create_anything_when_forbidden() throws Exception {
        String operatorToken = login("operator", "admin123");
        int before = countAllRoles();

        HttpResponse<String> response = send("POST", "/api/v1/roles",
                "{\"code\":\"sneaky_role\",\"name\":\"越权创建\"}", operatorToken);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(countAllRoles()).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE code = 'sneaky_role'", Integer.class)).isZero();
    }

    // ---------------------------------------------------------------- 辅助方法

    /**
     * 创建用户并返回 id；创建失败时返回 null。
     *
     * @param adminToken 管理员 Token
     * @param account    账号
     * @param password   密码
     * @param rolesJson  角色数组 JSON 片段
     * @return 用户 id 或 null
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private Long createUser(String adminToken, String account, String password, String rolesJson)
            throws IOException, InterruptedException {
        HttpResponse<String> response = send("POST", "/api/v1/users",
                "{\"account\":\"" + account + "\",\"password\":\"" + password + "\","
                        + "\"name\":\"e2e 用户\",\"roles\":" + rolesJson + "}", adminToken);
        if (response.statusCode() != 200) {
            return null;
        }
        return MAPPER.readTree(response.body()).get("data").get("id").asLong();
    }

    /**
     * 创建角色并登记以便清理。
     *
     * @param adminToken 管理员 Token
     * @param code       角色标识
     * @param name       角色名称
     * @return 角色 id
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private Long createRole(String adminToken, String code, String name)
            throws IOException, InterruptedException {
        HttpResponse<String> response = send("POST", "/api/v1/roles",
                "{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}", adminToken);
        assertThat(response.statusCode()).isEqualTo(200);
        Long roleId = MAPPER.readTree(response.body()).get("data").get("id").asLong();
        CREATED_ROLE_IDS.add(roleId);
        return roleId;
    }

    /**
     * 静默删除用户及其角色关联。
     *
     * @param userId 用户 id
     */
    private void deleteUserQuietly(Long userId) {
        if (userId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM user_roles WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    /**
     * 读取角色当前权限码。
     *
     * @param roleId 角色 id
     * @param token  管理员 Token
     * @return 权限码 JSON 数组
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private JsonNode readPermissions(Long roleId, String token) throws IOException, InterruptedException {
        HttpResponse<String> response = send("GET", "/api/v1/roles", null, token);
        for (JsonNode role : MAPPER.readTree(response.body()).get("data")) {
            if (role.get("id").asLong() == roleId) {
                return role.get("permissions");
            }
        }
        throw new IllegalStateException("角色不存在：" + roleId);
    }

    /**
     * 按角色标识查询角色 id。
     *
     * @param code 角色标识
     * @return 角色 id
     */
    private Long roleIdOf(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, code);
    }

    /**
     * 统计指定用户 id 是否存在。
     *
     * @param userId 用户 id
     * @return 数量
     */
    private int countUsers(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    /**
     * 统计用户角色关联数。
     *
     * @param userId 用户 id
     * @return 数量
     */
    private int countUserRoles(Long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_roles WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    /**
     * 统计角色数。
     *
     * @param roleId 角色 id
     * @return 数量
     */
    private int countRoles(Long roleId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE id = ?", Integer.class, roleId);
        return count == null ? 0 : count;
    }

    /**
     * 统计全部角色数。
     *
     * @return 数量
     */
    private int countAllRoles() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 仅用于验证权限变更闭环的探针接口。
     */
    @RestController
    @RequestMapping("/api/v1/test/admin-probe")
    static class AdminProbeController {

        /**
         * 需要 {@code sim:run} 权限。
         *
         * @return 固定响应
         */
        @GetMapping("/run-probe")
        @PreAuthorize("hasAuthority('sim:run')")
        public String runProbe() {
            return "{\"ok\":true}";
        }
    }
}
