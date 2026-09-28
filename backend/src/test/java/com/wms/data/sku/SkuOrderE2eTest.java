package com.wms.data.sku;

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
 * 货物与订单真实 HTTP 端到端测试（a，A-B3/B5）：验证 API-029 ~ API-036（FR-1.2、FR-1.3）。
 *
 * <p>重点验证：
 * <ol>
 *   <li><b>给 b、c 的核心输入字段口径</b>：{@code skuCode / weight / turnoverRate / priority / size}
 *       的 JSON 字段名与《接口文档》API-030 示例一致，且可写可读不丢失精度；</li>
 *   <li>{@code size} JSON 列的序列化/反序列化（这是 MyBatis-Plus typeHandler 的易错点）；</li>
 *   <li>出库仿真排序（FR-4.1）：列表默认按优先级降序 + 下达时间升序（C-B4 依赖）;</li>
 *   <li>删除保护：被库位占用或被订单引用的 SKU 不可删。</li>
 * </ol>
 *
 * <p>标记 {@code e2e}，执行：{@code mvn -s maven-settings.xml test -Pe2e -Dtest=SkuOrderE2eTest}。
 * 使用独立数据库 {@code wms_sim_e2e_sku}。
 *
 * @author a
 */
@Tag("e2e")
class SkuOrderE2eTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String ADMIN_HASH =
            "$2a$10$hm2S8wL2GKbgWQbQBhBkf.ZPXY34Oj6ImINkoWmuGSNvw1H7DxfAq";

    private static ConfigurableApplicationContext context;

    private static String baseUrl;

    private static JdbcTemplate jdbcTemplate;

    private static final List<Long> CREATED_SKU_IDS = new ArrayList<>();

    private static final List<Long> CREATED_ORDER_IDS = new ArrayList<>();

    /**
     * 启动真实应用并准备 admin 账号。
     */
    @BeforeAll
    static void startApplication() {
        // 命令行参数优先级最高；用 SpringApplicationBuilder.properties() 会被 application.yml 覆盖。
        context = new SpringApplicationBuilder(WmsApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/wms_sim_e2e_sku"
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

        jdbcTemplate.update("INSERT INTO users (id, account, password, name, status) "
                        + "VALUES (1, 'admin', ?, '系统管理员', 'active') "
                        + "ON DUPLICATE KEY UPDATE password = VALUES(password), status = 'active'",
                ADMIN_HASH);
        jdbcTemplate.update("INSERT IGNORE INTO user_roles (user_id, role_id) "
                + "SELECT 1, id FROM roles WHERE code = 'admin'");
    }

    /**
     * 关闭应用并清理测试数据。
     */
    @AfterAll
    static void stopApplication() {
        if (jdbcTemplate != null) {
            for (Long orderId : CREATED_ORDER_IDS) {
                jdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId);
            }
            jdbcTemplate.update("DELETE FROM orders WHERE remark = 'sku-order-e2e'");
            for (Long skuId : CREATED_SKU_IDS) {
                jdbcTemplate.update("UPDATE locations SET occupied_sku_id = NULL, status = 'free' "
                        + "WHERE occupied_sku_id = ?", skuId);
                jdbcTemplate.update("DELETE FROM skus WHERE id = ?", skuId);
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
     * @param token  JWT
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
     * 创建 SKU 并登记清理。
     *
     * @param token 管理员 Token
     * @param code  SKU 编码
     * @return 新建 SKU 的 data 节点
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private JsonNode createSku(String token, String code) throws IOException, InterruptedException {
        HttpResponse<String> response = send("POST", "/api/v1/skus",
                "{\"skuCode\":\"" + code + "\",\"name\":\"高频电子元件\",\"weight\":50,"
                        + "\"turnoverRate\":0.9,\"priority\":5,\"category\":\"电子\","
                        + "\"size\":{\"length\":30,\"width\":20,\"height\":10}}", token);
        assertThat(response.statusCode()).as("创建 SKU 失败：%s", response.body()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(response.body()).get("data");
        CREATED_SKU_IDS.add(data.get("id").asLong());
        return data;
    }

    // ------------------------------------------------------------ SKU（A-B3）

    @Test
    @DisplayName("API-030：创建 SKU 的入参/出参字段名与《接口文档》示例一致，weight/turnoverRate/size 不丢失")
    void should_create_sku_with_documented_field_names() throws Exception {
        String token = login("admin", "admin123");
        String code = "SKU-E2E-" + System.nanoTime() % 100000;

        JsonNode data = createSku(token, code);

        // 出参字段名（给 b、c 的消费字段）
        assertThat(data.get("skuCode").asText()).isEqualTo(code);
        assertThat(data.get("name").asText()).isEqualTo("高频电子元件");
        assertThat(data.get("weight").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("50"));
        assertThat(data.get("turnoverRate").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("0.9"));
        assertThat(data.get("priority").asInt()).isEqualTo(5);
        assertThat(data.get("category").asText()).isEqualTo("电子");
        assertThat(data.get("size").get("length").asInt()).isEqualTo(30);
        assertThat(data.get("size").get("width").asInt()).isEqualTo(20);
        assertThat(data.get("size").get("height").asInt()).isEqualTo(10);
        assertThat(data.get("volume").asInt()).isEqualTo(6000);
        assertThat(data.has("id")).isTrue();
    }

    @Test
    @DisplayName("size JSON 列：写入后重新查询（走 typeHandler）仍能正确反序列化")
    void should_round_trip_size_json_column() throws Exception {
        String token = login("admin", "admin123");
        String code = "SKU-SIZE-" + System.nanoTime() % 100000;
        JsonNode created = createSku(token, code);
        Long skuId = created.get("id").asLong();

        // 通过列表接口重新从数据库读取（触发 JacksonTypeHandler 反序列化）
        HttpResponse<String> list = send("GET", "/api/v1/skus?keyword=" + code, null, token);
        assertThat(list.statusCode()).isEqualTo(200);
        JsonNode item = MAPPER.readTree(list.body()).get("data").get("list").get(0);

        assertThat(item.get("id").asLong()).isEqualTo(skuId);
        assertThat(item.get("size").get("length").asInt()).isEqualTo(30);
        assertThat(item.get("size").get("height").asInt()).isEqualTo(10);

        // 数据库里存的应是合法 JSON 对象
        String rawSize = jdbcTemplate.queryForObject(
                "SELECT size FROM skus WHERE id = ?", String.class, skuId);
        assertThat(rawSize).contains("\"length\"").contains("30");
    }

    @Test
    @DisplayName("API-029：分页与品类/关键字过滤；API-031：更新核心输入字段并生效")
    void should_list_filter_and_update_sku() throws Exception {
        String token = login("admin", "admin123");
        String code = "SKU-UPD-" + System.nanoTime() % 100000;
        JsonNode created = createSku(token, code);
        Long skuId = created.get("id").asLong();

        HttpResponse<String> updated = send("PUT", "/api/v1/skus/" + skuId,
                "{\"weight\":123.456,\"turnoverRate\":2.5,\"priority\":1,\"name\":\"改名货物\"}", token);
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(updated.body()).get("data");
        assertThat(data.get("weight").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("123.456"));
        assertThat(data.get("turnoverRate").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("2.5"));
        assertThat(data.get("priority").asInt()).isEqualTo(1);
        assertThat(data.get("name").asText()).isEqualTo("改名货物");
        assertThat(data.get("skuCode").asText()).as("SKU 编码不可修改").isEqualTo(code);

        HttpResponse<String> byCategory = send("GET", "/api/v1/skus?category=电子&keyword=" + code,
                null, token);
        assertThat(MAPPER.readTree(byCategory.body()).get("data").get("total").asLong()).isEqualTo(1);
        HttpResponse<String> byOtherCategory = send("GET", "/api/v1/skus?category=食品&keyword=" + code,
                null, token);
        assertThat(MAPPER.readTree(byOtherCategory.body()).get("data").get("total").asLong()).isZero();
    }

    @Test
    @DisplayName("API-030/031：编码重复 42206；重量为负、优先级越界、尺寸为负均 40001")
    void should_reject_invalid_sku_requests() throws Exception {
        String token = login("admin", "admin123");
        String code = "SKU-DUP-" + System.nanoTime() % 100000;
        createSku(token, code);

        HttpResponse<String> duplicate = send("POST", "/api/v1/skus",
                "{\"skuCode\":\"" + code + "\",\"name\":\"重复\",\"weight\":1,"
                        + "\"turnoverRate\":1,\"priority\":1}", token);
        assertThat(duplicate.statusCode()).isEqualTo(422);
        assertThat(codeOf(duplicate)).isEqualTo(42206);

        HttpResponse<String> negativeWeight = send("POST", "/api/v1/skus",
                "{\"skuCode\":\"SKU-NEG\",\"name\":\"负重量\",\"weight\":-1,"
                        + "\"turnoverRate\":1,\"priority\":1}", token);
        assertThat(negativeWeight.statusCode()).isEqualTo(400);
        assertThat(codeOf(negativeWeight)).isEqualTo(40001);

        HttpResponse<String> badPriority = send("POST", "/api/v1/skus",
                "{\"skuCode\":\"SKU-PRI\",\"name\":\"越界优先级\",\"weight\":1,"
                        + "\"turnoverRate\":1,\"priority\":9}", token);
        assertThat(badPriority.statusCode()).isEqualTo(400);
        assertThat(MAPPER.readTree(badPriority.body()).get("message").asText())
                .contains("出库优先级最大为 5");

        HttpResponse<String> negativeSize = send("POST", "/api/v1/skus",
                "{\"skuCode\":\"SKU-SIZE-NEG\",\"name\":\"负尺寸\",\"weight\":1,"
                        + "\"turnoverRate\":1,\"priority\":1,\"size\":{\"length\":-1,"
                        + "\"width\":1,\"height\":1}}", token);
        assertThat(negativeSize.statusCode()).isEqualTo(400);
        assertThat(MAPPER.readTree(negativeSize.body()).get("message").asText())
                .contains("尺寸长不能为负数");
    }

    @Test
    @DisplayName("API-032：SKU 被订单引用时不可删除；无引用可删除；不存在返回 40405")
    void should_protect_sku_delete() throws Exception {
        String token = login("admin", "admin123");
        String code = "SKU-DEL-" + System.nanoTime() % 100000;
        Long skuId = createSku(token, code).get("id").asLong();

        Long orderId = createOrder(token, skuId, 3, 2, "2026-09-10T09:00:00");

        HttpResponse<String> inUse = send("DELETE", "/api/v1/skus/" + skuId, null, token);
        assertThat(inUse.statusCode()).isEqualTo(422);
        assertThat(codeOf(inUse)).isEqualTo(42210);
        assertThat(MAPPER.readTree(inUse.body()).get("message").asText()).contains("订单");

        jdbcTemplate.update("DELETE FROM orders WHERE id = ?", orderId);
        CREATED_ORDER_IDS.remove(orderId);

        assertThat(send("DELETE", "/api/v1/skus/" + skuId, null, token).statusCode()).isEqualTo(200);
        CREATED_SKU_IDS.remove(skuId);

        HttpResponse<String> missing = send("DELETE", "/api/v1/skus/" + skuId, null, token);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(codeOf(missing)).isEqualTo(40405);
    }

    @Test
    @DisplayName("API-032：SKU 被库位占用时不可删除（占用状态由库位模块维护）")
    void should_protect_sku_delete_when_occupied_by_location() throws Exception {
        String token = login("admin", "admin123");
        String code = "SKU-OCC-" + System.nanoTime() % 100000;
        Long skuId = createSku(token, code).get("id").asLong();

        // 构造一个占用该 SKU 的库位（货架/库位建模已在前一步验证，这里直接落库以便聚焦本步）
        jdbcTemplate.update("INSERT INTO warehouses (code, name) VALUES (?, '占用测试仓')",
                "WH-OCC-" + System.nanoTime() % 100000);
        Long warehouseId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("INSERT INTO racks (warehouse_id, code, aisle, column_count, layer_count) "
                + "VALUES (?, 'Z-01', 'Z', 1, 1)", warehouseId);
        Long rackId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbcTemplate.update("INSERT INTO locations (rack_id, warehouse_id, code, x, y, layer, status, "
                        + "occupied_sku_id) VALUES (?, ?, ?, 1, 1, 1, 'occupied', ?)",
                rackId, warehouseId, "Z-01-01-01-" + System.nanoTime() % 1000, skuId);
        Long locationId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        try {
            HttpResponse<String> inUse = send("DELETE", "/api/v1/skus/" + skuId, null, token);
            assertThat(inUse.statusCode()).isEqualTo(422);
            assertThat(codeOf(inUse)).isEqualTo(42210);
            assertThat(MAPPER.readTree(inUse.body()).get("message").asText()).contains("库位");
        } finally {
            jdbcTemplate.update("DELETE FROM locations WHERE id = ?", locationId);
            jdbcTemplate.update("DELETE FROM racks WHERE id = ?", rackId);
            jdbcTemplate.update("DELETE FROM warehouses WHERE id = ?", warehouseId);
        }
    }

    // ------------------------------------------------------------ 订单（A-B5）

    /**
     * 创建订单并登记清理。
     *
     * @param token    管理员 Token
     * @param skuId    货物 id
     * @param quantity 数量
     * @param priority 优先级
     * @param placedAt 下达时间，可为 null
     * @return 订单 id
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private Long createOrder(String token, Long skuId, int quantity, int priority, String placedAt)
            throws IOException, InterruptedException {
        String body = "{\"skuId\":" + skuId + ",\"quantity\":" + quantity
                + ",\"priority\":" + priority + ",\"remark\":\"sku-order-e2e\""
                + (placedAt == null ? "" : ",\"placedAt\":\"" + placedAt + "\"") + "}";
        HttpResponse<String> response = send("POST", "/api/v1/orders", body, token);
        assertThat(response.statusCode()).as("创建订单失败：%s", response.body()).isEqualTo(200);
        Long id = MAPPER.readTree(response.body()).get("data").get("id").asLong();
        CREATED_ORDER_IDS.add(id);
        return id;
    }

    @Test
    @DisplayName("API-034：创建订单返回字段与接口文档一致；未给订单号时自动生成 SO-yyyyMMdd-序号")
    void should_create_order_with_documented_shape_and_auto_order_no() throws Exception {
        String token = login("admin", "admin123");
        String code = "SKU-ORD-" + System.nanoTime() % 100000;
        Long skuId = createSku(token, code).get("id").asLong();

        HttpResponse<String> response = send("POST", "/api/v1/orders",
                "{\"skuId\":" + skuId + ",\"quantity\":20,\"priority\":4,"
                        + "\"placedAt\":\"2026-09-10T09:00:00\",\"remark\":\"sku-order-e2e\"}", token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(response.body()).get("data");
        CREATED_ORDER_IDS.add(data.get("id").asLong());

        assertThat(data.get("orderNo").asText()).matches("SO-\\d{8}-\\d{3}");
        assertThat(data.get("skuId").asLong()).isEqualTo(skuId);
        assertThat(data.get("skuCode").asText()).isEqualTo(code);
        assertThat(data.get("quantity").asInt()).isEqualTo(20);
        assertThat(data.get("priority").asInt()).isEqualTo(4);
        assertThat(data.get("status").asText()).isEqualTo("pending");
        assertThat(data.get("placedAt").asText()).startsWith("2026-09-10T09:00:00");
    }

    @Test
    @DisplayName("API-033：订单列表按「优先级降序 + 下达时间升序」排序（出库仿真 C-B4 的排序依据）")
    void should_sort_orders_by_priority_desc_then_placed_at_asc() throws Exception {
        String token = login("admin", "admin123");
        String code = "SKU-SORT-" + System.nanoTime() % 100000;
        Long skuId = createSku(token, code).get("id").asLong();

        // 故意乱序创建：低优先级先建、同优先级早下达时间后建
        Long low = createOrder(token, skuId, 1, 1, "2026-09-01T08:00:00");
        Long highLate = createOrder(token, skuId, 1, 5, "2026-09-03T08:00:00");
        Long highEarly = createOrder(token, skuId, 1, 5, "2026-09-02T08:00:00");
        Long mid = createOrder(token, skuId, 1, 3, "2026-09-01T09:00:00");

        HttpResponse<String> response = send("GET", "/api/v1/orders?sku_id=" + skuId, null, token);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode list = MAPPER.readTree(response.body()).get("data").get("list");
        assertThat(list.size()).isEqualTo(4);

        List<Long> orderedIds = new ArrayList<>();
        list.forEach(item -> orderedIds.add(item.get("id").asLong()));

        assertThat(orderedIds).as("优先级降序，同优先级按下达时间升序")
                .containsExactly(highEarly, highLate, mid, low);
    }

    @Test
    @DisplayName("API-034/035：订单号重复 42209；SKU 不存在 40405；优先级越界 40001；已完成订单禁止改数量")
    void should_reject_invalid_order_requests() throws Exception {
        String token = login("admin", "admin123");
        String code = "SKU-ORD2-" + System.nanoTime() % 100000;
        Long skuId = createSku(token, code).get("id").asLong();

        String orderNo = "SO-E2E-" + System.nanoTime() % 100000;
        HttpResponse<String> first = send("POST", "/api/v1/orders",
                "{\"orderNo\":\"" + orderNo + "\",\"skuId\":" + skuId + ",\"quantity\":1,"
                        + "\"priority\":1,\"remark\":\"sku-order-e2e\"}", token);
        Long orderId = MAPPER.readTree(first.body()).get("data").get("id").asLong();
        CREATED_ORDER_IDS.add(orderId);

        HttpResponse<String> duplicate = send("POST", "/api/v1/orders",
                "{\"orderNo\":\"" + orderNo + "\",\"skuId\":" + skuId + ",\"quantity\":1,"
                        + "\"priority\":1}", token);
        assertThat(duplicate.statusCode()).isEqualTo(422);
        assertThat(codeOf(duplicate)).isEqualTo(42209);

        HttpResponse<String> missingSku = send("POST", "/api/v1/orders",
                "{\"skuId\":99999999,\"quantity\":1,\"priority\":1}", token);
        assertThat(missingSku.statusCode()).isEqualTo(404);
        assertThat(codeOf(missingSku)).isEqualTo(40405);

        HttpResponse<String> badPriority = send("POST", "/api/v1/orders",
                "{\"skuId\":" + skuId + ",\"quantity\":1,\"priority\":0}", token);
        assertThat(badPriority.statusCode()).isEqualTo(400);
        assertThat(codeOf(badPriority)).isEqualTo(40001);

        // 置为已完成后再改数量 → 40001
        send("PUT", "/api/v1/orders/" + orderId, "{\"status\":\"completed\"}", token);
        HttpResponse<String> completedEdit = send("PUT", "/api/v1/orders/" + orderId,
                "{\"quantity\":5}", token);
        assertThat(completedEdit.statusCode()).isEqualTo(400);
        assertThat(MAPPER.readTree(completedEdit.body()).get("message").asText())
                .contains("已完成的订单");

        HttpResponse<String> missing = send("PUT", "/api/v1/orders/99999999", "{\"priority\":1}", token);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(codeOf(missing)).isEqualTo(40406);
    }

    @Test
    @DisplayName("API-033/035/036：状态过滤、更新状态、仅 pending/cancelled 可删除")
    void should_filter_update_and_delete_orders_by_status() throws Exception {
        String token = login("admin", "admin123");
        String code = "SKU-DEL2-" + System.nanoTime() % 100000;
        Long skuId = createSku(token, code).get("id").asLong();

        Long pendingOrder = createOrder(token, skuId, 2, 2, null);
        Long completedOrder = createOrder(token, skuId, 2, 2, null);

        // pending 可删
        assertThat(send("DELETE", "/api/v1/orders/" + pendingOrder, null, token).statusCode())
                .isEqualTo(200);
        CREATED_ORDER_IDS.remove(pendingOrder);

        // 置为 picking 后不可删
        send("PUT", "/api/v1/orders/" + completedOrder, "{\"status\":\"picking\"}", token);
        HttpResponse<String> inProgress = send("DELETE", "/api/v1/orders/" + completedOrder, null, token);
        assertThat(inProgress.statusCode()).isEqualTo(422);
        assertThat(codeOf(inProgress)).isEqualTo(42210);
        assertThat(MAPPER.readTree(inProgress.body()).get("message").asText()).contains("picking");

        // 取消后可删
        HttpResponse<String> cancelled = send("PUT", "/api/v1/orders/" + completedOrder,
                "{\"status\":\"cancelled\"}", token);
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(MAPPER.readTree(cancelled.body()).get("data").get("status").asText())
                .isEqualTo("cancelled");
        assertThat(send("DELETE", "/api/v1/orders/" + completedOrder, null, token).statusCode())
                .isEqualTo(200);
        CREATED_ORDER_IDS.remove(completedOrder);

        // 状态过滤
        HttpResponse<String> byStatus = send("GET", "/api/v1/orders?status=cancelled&sku_id=" + skuId,
                null, token);
        assertThat(MAPPER.readTree(byStatus.body()).get("data").get("total").asLong()).isZero();

        HttpResponse<String> badStatus = send("PUT", "/api/v1/orders/" + completedOrder,
                "{\"status\":\"unknown\"}", token);
        assertThat(badStatus.statusCode()).isEqualTo(400);
        assertThat(codeOf(badStatus)).isEqualTo(40001);
    }

    // ------------------------------------------------------------ 权限

    @Test
    @DisplayName("权限边界：读 sim:view 对四角色开放；写 sku:manage 仅 admin/operator，analyst/viewer 403")
    void should_enforce_sku_order_permissions() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "admin123");
        String analystToken = login("analyst", "admin123");
        String viewerToken = login("viewer", "admin123");

        // 四角色都能读
        for (String token : List.of(adminToken, operatorToken, analystToken, viewerToken)) {
            assertThat(send("GET", "/api/v1/skus", null, token).statusCode()).isEqualTo(200);
            assertThat(send("GET", "/api/v1/orders", null, token).statusCode()).isEqualTo(200);
        }

        // operator 可以写
        String code = "SKU-OP-" + System.nanoTime() % 100000;
        HttpResponse<String> operatorCreate = send("POST", "/api/v1/skus",
                "{\"skuCode\":\"" + code + "\",\"name\":\"操作员创建\",\"weight\":1,"
                        + "\"turnoverRate\":1,\"priority\":1}", operatorToken);
        assertThat(operatorCreate.statusCode()).as(operatorCreate.body()).isEqualTo(200);
        CREATED_SKU_IDS.add(MAPPER.readTree(operatorCreate.body()).get("data").get("id").asLong());

        // analyst / viewer 写一律 403（含带非法请求体的情况）
        String[][] writeEndpoints = {
                {"POST", "/api/v1/skus"},
                {"PUT", "/api/v1/skus/1"},
                {"DELETE", "/api/v1/skus/1"},
                {"POST", "/api/v1/orders"},
                {"PUT", "/api/v1/orders/1"},
                {"DELETE", "/api/v1/orders/1"}
        };
        for (String token : List.of(analystToken, viewerToken)) {
            for (String[] endpoint : writeEndpoints) {
                HttpResponse<String> response = send(endpoint[0], endpoint[1],
                        "POST".equals(endpoint[0]) || "PUT".equals(endpoint[0]) ? "{}" : null, token);
                assertThat(response.statusCode())
                        .as("%s %s 应 403，实际 %d", endpoint[0], endpoint[1], response.statusCode())
                        .isEqualTo(403);
                assertThat(codeOf(response)).isEqualTo(40301);
            }
        }

        assertThat(send("GET", "/api/v1/skus", null, null).statusCode()).isEqualTo(401);
    }
}
