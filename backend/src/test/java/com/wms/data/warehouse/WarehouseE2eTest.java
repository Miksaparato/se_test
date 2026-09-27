package com.wms.data.warehouse;

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
 * 仓库/货架/库位建模真实 HTTP 端到端测试（a，A-B1/B2）：验证 API-016 ~ API-028（FR-1.1）。
 *
 * <p>重点验证两件事：
 * <ol>
 *   <li>建模全流程可用：建仓库 → 建货架（自动生成库位）→ 查布局 → 改 → 删（含级联保护）；</li>
 *   <li><b>API-021 布局返回结构与《接口文档》示例逐字段一致</b>——
 *       这是 c 的平面图组件（C-F1）与 b 的推荐高亮的输入契约。</li>
 * </ol>
 *
 * <p>标记 {@code e2e}，执行：{@code mvn -s maven-settings.xml test -Pe2e -Dtest=WarehouseE2eTest}。
 * 使用独立数据库 {@code wms_sim_e2e_wh}。
 *
 * @author a
 */
@Tag("e2e")
class WarehouseE2eTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String ADMIN_HASH =
            "$2a$10$hm2S8wL2GKbgWQbQBhBkf.ZPXY34Oj6ImINkoWmuGSNvw1H7DxfAq";

    private static ConfigurableApplicationContext context;

    private static String baseUrl;

    private static JdbcTemplate jdbcTemplate;

    /** 清理用：本测试创建的仓库 id。 */
    private static final List<Long> CREATED_WAREHOUSE_IDS = new ArrayList<>();

    /** 占用测试用的临时 SKU id（locations.occupied_sku_id 有外键指向 skus）。 */
    private static Long occupiedSkuId;

    /**
     * 启动真实应用。
     */
    @BeforeAll
    static void startApplication() {
        context = new SpringApplicationBuilder(WmsApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/wms_sim_e2e_wh"
                                + "?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8"
                                + "&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                        "spring.datasource.username=root",
                        "spring.datasource.password=123456",
                        "spring.flyway.enabled=true",
                        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl",
                        "logging.level.root=WARN")
                .run();

        baseUrl = "http://127.0.0.1:"
                + context.getEnvironment().getProperty("local.server.port", Integer.class, 8080);
        jdbcTemplate = context.getBean(JdbcTemplate.class);

        jdbcTemplate.update("INSERT INTO users (id, account, password, name, status) "
                        + "VALUES (1, 'admin', ?, '系统管理员', 'active') "
                        + "ON DUPLICATE KEY UPDATE password = VALUES(password), status = 'active'",
                ADMIN_HASH);
        jdbcTemplate.update("INSERT IGNORE INTO user_roles (user_id, role_id) "
                + "SELECT 1, id FROM roles WHERE code = 'admin'");

        // occupied_sku_id 有外键指向 skus，占用状态测试需要一个真实存在的 SKU
        jdbcTemplate.update("INSERT INTO skus (id, code, name, weight, turnover_rate, priority) "
                        + "VALUES (1, 'SKU-E2E-001', 'e2e 测试货物', 50.000, 0.9000, 5) "
                        + "ON DUPLICATE KEY UPDATE name = VALUES(name)");
        occupiedSkuId = 1L;
    }

    /**
     * 关闭应用并清理测试数据。
     */
    @AfterAll
    static void stopApplication() {
        if (jdbcTemplate != null) {
            for (Long warehouseId : CREATED_WAREHOUSE_IDS) {
                jdbcTemplate.update("DELETE FROM locations WHERE warehouse_id = ?", warehouseId);
                jdbcTemplate.update("DELETE FROM racks WHERE warehouse_id = ?", warehouseId);
                jdbcTemplate.update("DELETE FROM warehouses WHERE id = ?", warehouseId);
            }
        }
        if (context != null) {
            context.close();
        }
    }

    /**
     * 发送请求。
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
                .timeout(Duration.ofSeconds(30))
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
        assertThat(response.statusCode()).as("登录失败：%s", response.body()).isEqualTo(200);
        return MAPPER.readTree(response.body()).get("data").get("token").asText();
    }

    /**
     * 读取业务错误码。
     *
     * @param response HTTP 响应
     * @return code
     * @throws IOException JSON 解析失败
     */
    private int codeOf(HttpResponse<String> response) throws IOException {
        return MAPPER.readTree(response.body()).get("code").asInt();
    }

    /**
     * 创建仓库并登记清理。
     *
     * @param token 管理员 Token
     * @param code  仓库编码
     * @param name  仓库名称
     * @return 仓库 id
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private Long createWarehouse(String token, String code, String name)
            throws IOException, InterruptedException {
        HttpResponse<String> response = send("POST", "/api/v1/warehouses",
                "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"length\":20,\"width\":10,"
                        + "\"height\":3,\"exitX\":0,\"exitY\":5}", token);
        assertThat(response.statusCode()).as("创建仓库失败：%s", response.body()).isEqualTo(200);
        Long id = MAPPER.readTree(response.body()).get("data").get("id").asLong();
        CREATED_WAREHOUSE_IDS.add(id);
        return id;
    }

    // ------------------------------------------------------------ 仓库 CRUD

    @Test
    @DisplayName("API-016/017/018：建仓库（含出库口）→ 列表 → 详情，字段与接口一致")
    void should_create_list_and_get_warehouse() throws Exception {
        String token = login("admin", "admin123");
        String code = "WH-E2E-" + System.nanoTime() % 100000;

        Long warehouseId = createWarehouse(token, code, "端到端一号仓");

        HttpResponse<String> detail = send("GET", "/api/v1/warehouses/" + warehouseId, null, token);
        assertThat(detail.statusCode()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(detail.body()).get("data");
        assertThat(data.get("code").asText()).isEqualTo(code);
        assertThat(data.get("name").asText()).isEqualTo("端到端一号仓");
        assertThat(data.get("length").asInt()).isEqualTo(20);
        assertThat(data.get("width").asInt()).isEqualTo(10);
        assertThat(data.get("height").asInt()).isEqualTo(3);
        assertThat(data.get("exit").get("x").asInt()).isZero();
        assertThat(data.get("exit").get("y").asInt()).isEqualTo(5);
        assertThat(data.get("rackCount").asLong()).isZero();

        HttpResponse<String> list = send("GET", "/api/v1/warehouses?keyword=" + code, null, token);
        assertThat(list.statusCode()).isEqualTo(200);
        JsonNode page = MAPPER.readTree(list.body()).get("data");
        assertThat(page.get("total").asLong()).isEqualTo(1);
        assertThat(page.get("list").get(0).get("code").asText()).isEqualTo(code);
        assertThat(page.get("page_size").asInt()).isEqualTo(20);
    }

    @Test
    @DisplayName("API-016：仓库编码重复返回 42205；坐标负数与缺编码返回 40001")
    void should_reject_invalid_warehouse_requests() throws Exception {
        String token = login("admin", "admin123");
        String code = "WH-DUP-" + System.nanoTime() % 100000;
        createWarehouse(token, code, "重复测试仓");

        HttpResponse<String> duplicate = send("POST", "/api/v1/warehouses",
                "{\"code\":\"" + code + "\",\"name\":\"重复\"}", token);
        assertThat(duplicate.statusCode()).isEqualTo(422);
        assertThat(codeOf(duplicate)).isEqualTo(42205);

        HttpResponse<String> negative = send("POST", "/api/v1/warehouses",
                "{\"code\":\"WH-NEG-1\",\"name\":\"负数\",\"exitX\":-1}", token);
        assertThat(negative.statusCode()).isEqualTo(400);
        assertThat(codeOf(negative)).isEqualTo(40001);

        HttpResponse<String> missingName = send("POST", "/api/v1/warehouses",
                "{\"code\":\"WH-NONAME\"}", token);
        assertThat(missingName.statusCode()).isEqualTo(400);
        assertThat(MAPPER.readTree(missingName.body()).get("message").asText()).contains("仓库名称不能为空");
    }

    @Test
    @DisplayName("API-019/020：更新仓库生效；删除仓库与不存在资源分别返回 42210 与 40402")
    void should_update_and_delete_warehouse() throws Exception {
        String token = login("admin", "admin123");
        String code = "WH-UPD-" + System.nanoTime() % 100000;
        Long warehouseId = createWarehouse(token, code, "待改仓库");

        HttpResponse<String> updated = send("PUT", "/api/v1/warehouses/" + warehouseId,
                "{\"name\":\"改后仓库\",\"exitX\":1,\"exitY\":2}", token);
        assertThat(updated.statusCode()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(updated.body()).get("data");
        assertThat(data.get("name").asText()).isEqualTo("改后仓库");
        assertThat(data.get("exit").get("x").asInt()).isEqualTo(1);
        assertThat(data.get("exit").get("y").asInt()).isEqualTo(2);
        assertThat(data.get("code").asText()).as("仓库编码不可修改").isEqualTo(code);

        // 有货架时不允许删除仓库
        send("POST", "/api/v1/racks",
                "{\"warehouseId\":" + warehouseId + ",\"code\":\"R-01\",\"aisle\":\"R\","
                        + "\"columnCount\":1,\"layerCount\":1}", token);
        HttpResponse<String> inUse = send("DELETE", "/api/v1/warehouses/" + warehouseId, null, token);
        assertThat(inUse.statusCode()).isEqualTo(422);
        assertThat(codeOf(inUse)).isEqualTo(42210);
        assertThat(MAPPER.readTree(inUse.body()).get("message").asText()).contains("货架");

        jdbcTemplate.update("DELETE FROM racks WHERE warehouse_id = ?", warehouseId);
        assertThat(send("DELETE", "/api/v1/warehouses/" + warehouseId, null, token).statusCode())
                .isEqualTo(200);
        assertThat(countWarehouse(warehouseId)).isZero();
        CREATED_WAREHOUSE_IDS.remove(warehouseId);

        HttpResponse<String> missing = send("DELETE", "/api/v1/warehouses/99999999", null, token);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(codeOf(missing)).isEqualTo(40402);
    }

    // ------------------------------------------------------------ 货架与库位批量生成

    @Test
    @DisplayName("API-022：建货架并批量生成库位——编码 A-01-列-层、坐标按 row 递增、层号自 1 起")
    void should_generate_locations_with_correct_code_coordinate_and_layer() throws Exception {
        String token = login("admin", "admin123");
        String whCode = "WH-GEN-" + System.nanoTime() % 100000;
        Long warehouseId = createWarehouse(token, whCode, "生成测试仓");

        HttpResponse<String> rack = send("POST", "/api/v1/racks",
                "{\"warehouseId\":" + warehouseId + ",\"code\":\"A-01\",\"aisle\":\"A\","
                        + "\"columnCount\":3,\"layerCount\":2,\"x\":3,\"y\":2,"
                        + "\"orientation\":\"row\",\"generateLocations\":true}", token);
        assertThat(rack.statusCode()).as("建货架失败：%s", rack.body()).isEqualTo(200);
        JsonNode rackData = MAPPER.readTree(rack.body()).get("data");
        Long rackId = rackData.get("id").asLong();
        assertThat(rackData.get("locationCount").asLong()).isEqualTo(6);

        // 用 MySQL 直接核对生成的库位（编码/坐标/层/状态/容量/仓库冗余列）
        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT code, x, y, layer, status, capacity, warehouse_id FROM locations "
                        + "WHERE rack_id = ? ORDER BY code", rackId);
        assertThat(rows).hasSize(6);
        assertThat(rows.get(0).get("code")).isEqualTo("A-01-01-01");
        assertThat(rows.get(5).get("code")).isEqualTo("A-01-03-02");

        // 第 1 列第 1 层：x = 3 + 0 = 3，y = 2，layer = 1
        assertThat(((Number) rows.get(0).get("x")).intValue()).isEqualTo(3);
        assertThat(((Number) rows.get(0).get("y")).intValue()).isEqualTo(2);
        assertThat(((Number) rows.get(0).get("layer")).intValue()).isEqualTo(1);
        // 第 3 列第 1 层：x = 3 + 2 = 5
        assertThat(((Number) rows.get(4).get("x")).intValue()).isEqualTo(5);
        assertThat(((Number) rows.get(4).get("layer")).intValue()).isEqualTo(1);
        // 第 3 列第 2 层：同一平面坐标，层号为 2
        assertThat(((Number) rows.get(5).get("x")).intValue()).isEqualTo(5);
        assertThat(((Number) rows.get(5).get("layer")).intValue()).isEqualTo(2);

        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("status")).isEqualTo("free");
            // JDBC 返回的整型列可能是 Integer/Long，统一按 Number 比较
            assertThat(((Number) row.get("warehouse_id")).longValue()).isEqualTo(warehouseId);
            assertThat(((java.math.BigDecimal) row.get("capacity")))
                    .isEqualByComparingTo(new java.math.BigDecimal("100.000"));
        });
    }

    @Test
    @DisplayName("API-022：orientation=column 时坐标沿 y 递增；同一仓库货架编码重复返回 40001")
    void should_generate_column_oriented_locations_and_reject_duplicate_rack_code() throws Exception {
        String token = login("admin", "admin123");
        String whCode = "WH-COL-" + System.nanoTime() % 100000;
        Long warehouseId = createWarehouse(token, whCode, "列向测试仓");

        HttpResponse<String> rack = send("POST", "/api/v1/racks",
                "{\"warehouseId\":" + warehouseId + ",\"code\":\"B-01\",\"aisle\":\"B\","
                        + "\"columnCount\":2,\"layerCount\":1,\"x\":1,\"y\":7,"
                        + "\"orientation\":\"column\",\"generateLocations\":true}", token);
        Long rackId = MAPPER.readTree(rack.body()).get("data").get("id").asLong();

        List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT code, x, y FROM locations WHERE rack_id = ? ORDER BY code", rackId);
        assertThat(rows).hasSize(2);
        assertThat(((Number) rows.get(0).get("x")).intValue()).isEqualTo(1);
        assertThat(((Number) rows.get(0).get("y")).intValue()).isEqualTo(7);
        assertThat(((Number) rows.get(1).get("x")).intValue()).isEqualTo(1);
        assertThat(((Number) rows.get(1).get("y")).intValue()).isEqualTo(8);

        HttpResponse<String> duplicate = send("POST", "/api/v1/racks",
                "{\"warehouseId\":" + warehouseId + ",\"code\":\"B-01\",\"aisle\":\"B\","
                        + "\"columnCount\":1,\"layerCount\":1}", token);
        assertThat(duplicate.statusCode()).isEqualTo(400);
        assertThat(codeOf(duplicate)).isEqualTo(40001);

        HttpResponse<String> badOrientation = send("POST", "/api/v1/racks",
                "{\"warehouseId\":" + warehouseId + ",\"code\":\"B-02\",\"aisle\":\"B\","
                        + "\"columnCount\":1,\"layerCount\":1,\"orientation\":\"diagonal\"}", token);
        assertThat(badOrientation.statusCode()).isEqualTo(400);

        HttpResponse<String> missingWarehouse = send("POST", "/api/v1/racks",
                "{\"warehouseId\":99999999,\"code\":\"B-03\",\"aisle\":\"B\","
                        + "\"columnCount\":1,\"layerCount\":1}", token);
        assertThat(missingWarehouse.statusCode()).isEqualTo(404);
        assertThat(codeOf(missingWarehouse)).isEqualTo(40402);
    }

    @Test
    @DisplayName("API-023/024：改货架生效；有库位时删除货架返回 42210；无库位可删")
    void should_update_rack_and_protect_delete() throws Exception {
        String token = login("admin", "admin123");
        String whCode = "WH-RACK-" + System.nanoTime() % 100000;
        Long warehouseId = createWarehouse(token, whCode, "货架测试仓");

        HttpResponse<String> created = send("POST", "/api/v1/racks",
                "{\"warehouseId\":" + warehouseId + ",\"code\":\"C-01\",\"aisle\":\"C\","
                        + "\"columnCount\":2,\"layerCount\":1,\"generateLocations\":true}", token);
        Long rackId = MAPPER.readTree(created.body()).get("data").get("id").asLong();

        HttpResponse<String> updated = send("PUT", "/api/v1/racks/" + rackId,
                "{\"x\":9,\"y\":4,\"orientation\":\"column\"}", token);
        assertThat(updated.statusCode()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(updated.body()).get("data");
        assertThat(data.get("x").asInt()).isEqualTo(9);
        assertThat(data.get("y").asInt()).isEqualTo(4);
        assertThat(data.get("orientation").asText()).isEqualTo("column");
        assertThat(data.get("locationCount").asLong()).isEqualTo(2);

        HttpResponse<String> inUse = send("DELETE", "/api/v1/racks/" + rackId, null, token);
        assertThat(inUse.statusCode()).isEqualTo(422);
        assertThat(codeOf(inUse)).isEqualTo(42210);
        assertThat(MAPPER.readTree(inUse.body()).get("message").asText()).contains("库位");

        jdbcTemplate.update("DELETE FROM locations WHERE rack_id = ?", rackId);
        assertThat(send("DELETE", "/api/v1/racks/" + rackId, null, token).statusCode()).isEqualTo(200);

        HttpResponse<String> missing = send("PUT", "/api/v1/racks/99999999", "{\"x\":1}", token);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(codeOf(missing)).isEqualTo(40403);
    }

    // ------------------------------------------------------------ 库位 CRUD

    @Test
    @DisplayName("API-025/026/027/028：库位增删改查、状态一致性校验、占用状态不可删除")
    void should_manage_locations_with_status_consistency() throws Exception {
        String token = login("admin", "admin123");
        String whCode = "WH-LOC-" + System.nanoTime() % 100000;
        Long warehouseId = createWarehouse(token, whCode, "库位测试仓");
        Long rackId = MAPPER.readTree(send("POST", "/api/v1/racks",
                "{\"warehouseId\":" + warehouseId + ",\"code\":\"D-01\",\"aisle\":\"D\","
                        + "\"columnCount\":1,\"layerCount\":2}", token).body())
                .get("data").get("id").asLong();

        String code = "D-01-01-01-" + System.nanoTime() % 1000;

        // 层号超出货架层数 → 40001
        HttpResponse<String> badLayer = send("POST", "/api/v1/locations",
                "{\"rackId\":" + rackId + ",\"code\":\"" + code + "\",\"x\":1,\"y\":1,\"layer\":5}",
                token);
        assertThat(badLayer.statusCode()).isEqualTo(400);
        assertThat(MAPPER.readTree(badLayer.body()).get("message").asText()).contains("超出货架");

        // occupied 但未给 skuId → 40001
        HttpResponse<String> noSku = send("POST", "/api/v1/locations",
                "{\"rackId\":" + rackId + ",\"code\":\"" + code + "\",\"x\":1,\"y\":1,\"layer\":1,"
                        + "\"status\":\"occupied\"}", token);
        assertThat(noSku.statusCode()).isEqualTo(400);
        assertThat(MAPPER.readTree(noSku.body()).get("message").asText()).contains("occupiedSkuId");

        // 正常创建
        HttpResponse<String> created = send("POST", "/api/v1/locations",
                "{\"rackId\":" + rackId + ",\"code\":\"" + code + "\",\"x\":1,\"y\":1,\"layer\":1,"
                        + "\"capacity\":250}", token);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(created.body()).get("data");
        Long locationId = data.get("id").asLong();
        assertThat(data.get("status").asText()).isEqualTo("free");
        assertThat(data.get("warehouseId").asLong())
                .as("冗余 warehouse_id 应自动取自货架").isEqualTo(warehouseId);
        assertThat(data.get("capacity").asDouble()).isEqualTo(250.0);

        // 编码重复 → 42204
        HttpResponse<String> duplicate = send("POST", "/api/v1/locations",
                "{\"rackId\":" + rackId + ",\"code\":\"" + code + "\",\"x\":2,\"y\":2,\"layer\":1}",
                token);
        assertThat(duplicate.statusCode()).isEqualTo(422);
        assertThat(codeOf(duplicate)).isEqualTo(42204);

        // 更新为占用（skuId 必须真实存在，否则违反 locations.occupied_sku_id 外键）
        HttpResponse<String> occupied = send("PUT", "/api/v1/locations/" + locationId,
                "{\"status\":\"occupied\",\"occupiedSkuId\":" + occupiedSkuId + "}", token);
        assertThat(occupied.statusCode()).as("占用失败：%s", occupied.body()).isEqualTo(200);
        assertThat(MAPPER.readTree(occupied.body()).get("data").get("status").asText())
                .isEqualTo("occupied");
        assertThat(MAPPER.readTree(occupied.body()).get("data").get("occupiedSkuId").asLong())
                .isEqualTo(occupiedSkuId);

        // 占用状态不可删除
        HttpResponse<String> deleteOccupied = send("DELETE", "/api/v1/locations/" + locationId, null, token);
        assertThat(deleteOccupied.statusCode()).isEqualTo(422);
        assertThat(codeOf(deleteOccupied)).isEqualTo(42210);

        // 退回空闲应清空占用货物
        HttpResponse<String> freed = send("PUT", "/api/v1/locations/" + locationId,
                "{\"status\":\"free\"}", token);
        assertThat(freed.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(freed.body()).get("data").get("occupiedSkuId").isNull()).isTrue();

        // 列表过滤
        HttpResponse<String> byRack = send("GET", "/api/v1/locations?rack_id=" + rackId, null, token);
        assertThat(MAPPER.readTree(byRack.body()).get("data").get("total").asLong()).isEqualTo(1);
        HttpResponse<String> byStatus = send("GET", "/api/v1/locations?warehouse_id=" + warehouseId
                + "&status=occupied", null, token);
        assertThat(MAPPER.readTree(byStatus.body()).get("data").get("total").asLong()).isZero();

        // 删除
        assertThat(send("DELETE", "/api/v1/locations/" + locationId, null, token).statusCode()).isEqualTo(200);
        HttpResponse<String> missing = send("DELETE", "/api/v1/locations/" + locationId, null, token);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(codeOf(missing)).isEqualTo(40404);
    }

    // ------------------------------------------------------------ API-021 布局（给 c 的冻结契约）

    @Test
    @DisplayName("API-021：布局结构含 warehouseId/name/exit 与嵌套 racks[].locations[]，字段名与接口文档一致")
    void should_return_layout_matching_interface_document() throws Exception {
        String token = login("admin", "admin123");
        String whCode = "WH-LAY-" + System.nanoTime() % 100000;
        Long warehouseId = createWarehouse(token, whCode, "布局测试仓");
        send("POST", "/api/v1/racks",
                "{\"warehouseId\":" + warehouseId + ",\"code\":\"E-01\",\"aisle\":\"E\","
                        + "\"columnCount\":2,\"layerCount\":2,\"generateLocations\":true}", token);
        send("POST", "/api/v1/racks",
                "{\"warehouseId\":" + warehouseId + ",\"code\":\"F-01\",\"aisle\":\"F\","
                        + "\"columnCount\":1,\"layerCount\":1,\"generateLocations\":true}", token);

        HttpResponse<String> response = send("GET", "/api/v1/warehouses/" + warehouseId + "/layout",
                null, token);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(response.body()).get("data");

        // 顶层字段（《接口文档》API-021 示例：warehouseId / name / exit / racks）
        assertThat(data.has("warehouseId")).isTrue();
        assertThat(data.get("warehouseId").asLong()).isEqualTo(warehouseId);
        assertThat(data.get("name").asText()).isEqualTo("布局测试仓");
        assertThat(data.get("exit").get("x").asInt()).isZero();
        assertThat(data.get("exit").get("y").asInt()).isEqualTo(5);
        assertThat(data.get("racks").isArray()).isTrue();
        assertThat(data.get("racks").size()).isEqualTo(2);

        // 货架节点（示例：rackId / aisle / locations）
        JsonNode rack = data.get("racks").get(0);
        assertThat(rack.has("rackId")).isTrue();
        assertThat(rack.get("aisle").asText()).isEqualTo("E");
        assertThat(rack.get("locations").size()).isEqualTo(4);

        // 库位节点（示例：id / code / x / y / layer / status / capacity）
        JsonNode location = rack.get("locations").get(0);
        assertThat(location.has("id")).isTrue();
        assertThat(location.has("code")).isTrue();
        assertThat(location.has("x")).isTrue();
        assertThat(location.has("y")).isTrue();
        assertThat(location.has("layer")).isTrue();
        assertThat(location.get("status").asText()).isEqualTo("free");
        assertThat(location.has("capacity")).isTrue();

        // 布局节点不应夹带创建时间等无关字段
        assertThat(location.has("createdAt")).isFalse();
        assertThat(location.has("rackId")).isFalse();

        HttpResponse<String> missing = send("GET", "/api/v1/warehouses/99999999/layout", null, token);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(codeOf(missing)).isEqualTo(40402);
    }

    // ------------------------------------------------------------ 权限

    @Test
    @DisplayName("权限边界：写操作需 warehouse:manage（非 admin 一律 403），读操作 sim:view 对四角色开放")
    void should_enforce_permission_boundaries() throws Exception {
        String adminToken = login("admin", "admin123");
        String whCode = "WH-PERM-" + System.nanoTime() % 100000;
        Long warehouseId = createWarehouse(adminToken, whCode, "权限测试仓");

        String operatorToken = login("operator", "admin123");
        String analystToken = login("analyst", "admin123");
        String viewerToken = login("viewer", "admin123");

        // 四种角色都能读布局与仓库列表（sim:view）
        for (String token : List.of(adminToken, operatorToken, analystToken, viewerToken)) {
            assertThat(send("GET", "/api/v1/warehouses/" + warehouseId + "/layout", null, token).statusCode())
                    .isEqualTo(200);
            assertThat(send("GET", "/api/v1/warehouses", null, token).statusCode()).isEqualTo(200);
            assertThat(send("GET", "/api/v1/locations?warehouse_id=" + warehouseId, null, token)
                    .statusCode()).isEqualTo(200);
        }

        // 非 admin 写操作一律 403
        String[][] writeEndpoints = {
                {"POST", "/api/v1/warehouses"},
                {"PUT", "/api/v1/warehouses/" + warehouseId},
                {"DELETE", "/api/v1/warehouses/" + warehouseId},
                {"POST", "/api/v1/racks"},
                {"PUT", "/api/v1/racks/1"},
                {"DELETE", "/api/v1/racks/1"},
                {"POST", "/api/v1/locations"},
                {"PUT", "/api/v1/locations/1"},
                {"DELETE", "/api/v1/locations/1"}
        };
        for (String token : List.of(operatorToken, analystToken, viewerToken)) {
            for (String[] endpoint : writeEndpoints) {
                HttpResponse<String> response = send(endpoint[0], endpoint[1],
                        "POST".equals(endpoint[0]) || "PUT".equals(endpoint[0]) ? "{}" : null, token);
                assertThat(response.statusCode())
                        .as("%s %s 应 403，实际 %d", endpoint[0], endpoint[1], response.statusCode())
                        .isEqualTo(403);
                assertThat(codeOf(response)).isEqualTo(40301);
            }
        }

        // 未登录 401
        assertThat(send("GET", "/api/v1/warehouses", null, null).statusCode()).isEqualTo(401);
    }

    /**
     * 统计仓库是否存在。
     *
     * @param warehouseId 仓库 id
     * @return 数量
     */
    private int countWarehouse(Long warehouseId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM warehouses WHERE id = ?", Integer.class, warehouseId);
        return count == null ? 0 : count;
    }
}
