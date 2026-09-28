package com.wms.data.importexport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.WmsApplication;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导入导出真实 HTTP 端到端测试（a，A-B4）：验证 API-037 ~ API-040（FR-1.2 验收 2、NFR-7）。
 *
 * <p>重点验证：
 * <ol>
 *   <li>导入**不因个别行非法而整体失败**：合法行入库、非法行逐行给出行号与原因；</li>
 *   <li>CSV 与 Excel(.xlsx) 两种格式走同一套校验逻辑；</li>
 *   <li>重复编码策略 skip / update；</li>
 *   <li>导出 CSV 带 UTF-8 BOM，**中文回读不乱码**（Excel 兼容性的关键）；</li>
 *   <li>导出内容可再次导入（往返一致）。</li>
 * </ol>
 *
 * <p>标记 {@code e2e}，执行：{@code mvn -s maven-settings.xml test -Pe2e -Dtest=ImportExportE2eTest}。
 *
 * @author a
 */
@Tag("e2e")
class ImportExportE2eTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final RestTemplate REST = buildRestTemplate();

    /**
     * 构造不抛异常的 RestTemplate：导入接口的 4xx（越权 403、参数 400）是断言目标，
     * 默认的 DefaultResponseErrorHandler 会直接抛异常，导致拿不到响应体。
     *
     * @return RestTemplate
     */
    private static RestTemplate buildRestTemplate() {
        RestTemplate template = new RestTemplate();
        template.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(org.springframework.http.client.ClientHttpResponse response) {
                // 不做任何处理，交由测试断言状态码
            }
        });
        return template;
    }

    private static final String ADMIN_HASH =
            "$2a$10$hm2S8wL2GKbgWQbQBhBkf.ZPXY34Oj6ImINkoWmuGSNvw1H7DxfAq";

    private static ConfigurableApplicationContext context;

    private static String baseUrl;

    private static JdbcTemplate jdbcTemplate;

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
                        "--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/wms_sim_e2e_imp"
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
        jdbcTemplate.update("DELETE FROM orders WHERE remark LIKE 'imp-e2e%'");
        jdbcTemplate.update("DELETE FROM skus WHERE code LIKE 'IMP-E2E-%'");
    }

    /**
     * 关闭应用并清理测试数据。
     */
    @AfterAll
    static void stopApplication() {
        if (jdbcTemplate != null) {
            jdbcTemplate.update("DELETE FROM orders WHERE remark LIKE 'imp-e2e%'");
            jdbcTemplate.update("DELETE FROM skus WHERE code LIKE 'IMP-E2E-%'");
        }
        if (context != null) {
            context.close();
        }
    }

    /**
     * 登录并取回 Token。
     *
     * @return JWT
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private String login() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/auth/login"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"account\":\"admin\",\"password\":\"admin123\"}", StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(
                StandardCharsets.UTF_8));
        assertThat(response.statusCode()).as("登录失败：%s", response.body()).isEqualTo(200);
        return MAPPER.readTree(response.body()).get("data").get("token").asText();
    }

    /**
     * 以 multipart 上传文件到导入接口。
     *
     * @param token    JWT
     * @param path     接口路径
     * @param filename 文件名（决定解析器）
     * @param content  文件内容
     * @param strategy 重复策略，可为 null
     * @return HTTP 响应
     */
    private ResponseEntity<String> upload(String token, String path, String filename,
                                          byte[] content, String strategy) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        if (strategy != null) {
            body.add("strategy", strategy);
        }
        return REST.exchange(baseUrl + path, HttpMethod.POST, new HttpEntity<>(body, headers),
                String.class);
    }

    /**
     * 下载导出文件。
     *
     * @param token JWT
     * @param path  接口路径
     * @return 响应（字节内容）
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private HttpResponse<byte[]> download(String token, String path)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    /**
     * 构造一个 .xlsx 文件。
     *
     * @param headers 表头
     * @param rows    数据行
     * @return 文件字节
     * @throws IOException 写入失败
     */
    private byte[] buildXlsx(String[] headers, String[][] rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("data");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    String value = rows[r][c];
                    if (value == null) {
                        continue;
                    }
                    // 数值列写成数值型，验证 DataFormatter/BigDecimal 处理
                    if (value.matches("^-?\\d+(\\.\\d+)?$")) {
                        row.createCell(c).setCellValue(Double.parseDouble(value));
                    } else {
                        row.createCell(c).setCellValue(value);
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------ 导入

    @Test
    @DisplayName("API-037（CSV）：合法行入库、非法行逐行返回行号与原因，整体不失败")
    void should_import_csv_with_per_line_errors() throws Exception {
        String token = login();
        String code1 = "IMP-E2E-" + System.nanoTime() % 100000 + "-A";
        String code3 = "IMP-E2E-" + System.nanoTime() % 100000 + "-C";

        String csv = "skuCode,name,weight,turnoverRate,priority,category,length,width,height,remark\n"
                // 第 2 行：合法（含中文与带逗号的引号字段）
                + code1 + ",\"高频,电子元件\",50,0.9,5,电子,30,20,10,imp-e2e\n"
                // 第 3 行：重量非法
                + "IMP-E2E-BAD-W,坏重量,abc,0.9,5,电子,,,,\n"
                // 第 4 行：合法
                + code3 + ",螺丝,1.5,0.2,2,五金,5,5,5,imp-e2e\n"
                // 第 5 行：优先级越界
                + "IMP-E2E-BAD-P,坏优先级,1,1,9,五金,,,,\n"
                // 第 6 行：缺名称
                + "IMP-E2E-BAD-N,,1,1,1,五金,,,,\n";

        ResponseEntity<String> response = upload(token, "/api/v1/import/skus", "skus.csv",
                csv.getBytes(StandardCharsets.UTF_8), null);

        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(response.getBody()).get("data");
        assertThat(data.get("total").asInt()).isEqualTo(5);
        assertThat(data.get("inserted").asInt()).isEqualTo(2);
        assertThat(data.get("failed").asInt()).isEqualTo(3);
        assertThat(data.get("skipped").asInt()).isZero();

        JsonNode errors = data.get("errors");
        assertThat(errors.size()).isEqualTo(3);
        assertThat(errors.get(0).get("line").asInt()).isEqualTo(3);
        assertThat(errors.get(0).get("column").asText()).isEqualTo("weight");
        assertThat(errors.get(1).get("line").asInt()).isEqualTo(5);
        assertThat(errors.get(1).get("column").asText()).isEqualTo("priority");
        assertThat(errors.get(2).get("line").asInt()).isEqualTo(6);
        assertThat(errors.get(2).get("column").asText()).isEqualTo("name");

        // 合法行确实入库，且中文与引号字段正确
        assertThat(countSku(code1)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM skus WHERE code = ?", String.class, code1))
                .isEqualTo("高频,电子元件");
        assertThat(countSku(code3)).isEqualTo(1);
        assertThat(countSku("IMP-E2E-BAD-W")).isZero();
    }

    @Test
    @DisplayName("API-037：带 UTF-8 BOM 的 CSV（Excel 另存为）也能正常导入")
    void should_import_csv_with_bom() throws Exception {
        String token = login();
        String code = "IMP-E2E-BOM-" + System.nanoTime() % 100000;
        String csv = "\ufeffskuCode,name,weight,turnoverRate,priority\n"
                + code + ",BOM 测试,1,1,1\n";

        ResponseEntity<String> response = upload(token, "/api/v1/import/skus", "skus-bom.csv",
                csv.getBytes(StandardCharsets.UTF_8), null);

        JsonNode data = MAPPER.readTree(response.getBody()).get("data");
        assertThat(data.get("inserted").asInt()).as("BOM 不应导致列名失配：%s", response.getBody())
                .isEqualTo(1);
        assertThat(data.get("failed").asInt()).isZero();
        assertThat(countSku(code)).isEqualTo(1);
    }

    @Test
    @DisplayName("API-037（Excel .xlsx）：与 CSV 走同一套校验，数值单元格正确解析")
    void should_import_xlsx() throws Exception {
        String token = login();
        String code = "IMP-E2E-XLSX-" + System.nanoTime() % 100000;
        byte[] xlsx = buildXlsx(
                new String[]{"skuCode", "name", "weight", "turnoverRate", "priority", "length", "width", "height"},
                new String[][]{
                        {code, "Excel 货物", "12.5", "0.75", "3", "10", "20", "30"},
                        {"IMP-E2E-XLSX-BAD", "坏行", "1", "1", "9", "", "", ""}
                });

        ResponseEntity<String> response = upload(token, "/api/v1/import/skus", "skus.xlsx", xlsx, null);

        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(response.getBody()).get("data");
        assertThat(data.get("inserted").asInt()).isEqualTo(1);
        assertThat(data.get("failed").asInt()).isEqualTo(1);
        assertThat(data.get("errors").get(0).get("line").asInt()).isEqualTo(3);

        // 数值单元格：12.5 不应变成 12.500000000001 之类
        assertThat(jdbcTemplate.queryForObject(
                "SELECT weight FROM skus WHERE code = ?", java.math.BigDecimal.class, code))
                .isEqualByComparingTo(new java.math.BigDecimal("12.5"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT turnover_rate FROM skus WHERE code = ?", java.math.BigDecimal.class, code))
                .isEqualByComparingTo(new java.math.BigDecimal("0.75"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT size FROM skus WHERE code = ?", String.class, code))
                .contains("\"length\"").contains("10");
    }

    @Test
    @DisplayName("API-037：重复编码策略 skip（默认跳过）与 update（覆盖更新）")
    void should_respect_duplicate_strategy() throws Exception {
        String token = login();
        String code = "IMP-E2E-DUP-" + System.nanoTime() % 100000;
        String csv = "skuCode,name,weight,turnoverRate,priority\n" + code + ",原名,1,1,1\n";

        assertThat(MAPPER.readTree(upload(token, "/api/v1/import/skus", "skus.csv",
                csv.getBytes(StandardCharsets.UTF_8), null).getBody()).get("data").get("inserted").asInt())
                .isEqualTo(1);

        // 默认 skip：跳过且不报错
        JsonNode skipResult = MAPPER.readTree(upload(token, "/api/v1/import/skus", "skus.csv",
                csv.getBytes(StandardCharsets.UTF_8), null).getBody()).get("data");
        assertThat(skipResult.get("skipped").asInt()).isEqualTo(1);
        assertThat(skipResult.get("inserted").asInt()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM skus WHERE code = ?", String.class, code))
                .isEqualTo("原名");

        // update：覆盖名称
        String updatedCsv = "skuCode,name,weight,turnoverRate,priority\n" + code + ",新名,9,9,4\n";
        JsonNode updateResult = MAPPER.readTree(upload(token, "/api/v1/import/skus", "skus.csv",
                updatedCsv.getBytes(StandardCharsets.UTF_8), "update").getBody()).get("data");
        assertThat(updateResult.get("updated").asInt()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM skus WHERE code = ?", String.class, code))
                .isEqualTo("新名");
        assertThat(jdbcTemplate.queryForObject("SELECT priority FROM skus WHERE code = ?",
                Integer.class, code)).isEqualTo(4);

        // 非法策略参数 → 40001
        ResponseEntity<String> badStrategy = upload(token, "/api/v1/import/skus", "skus.csv",
                csv.getBytes(StandardCharsets.UTF_8), "overwrite");
        assertThat(badStrategy.getStatusCode().value()).isEqualTo(400);
        assertThat(MAPPER.readTree(badStrategy.getBody()).get("code").asInt()).isEqualTo(40001);
    }

    @Test
    @DisplayName("API-037：文件内编码重复被识别并报错；不支持的后缀与空文件返回 40001")
    void should_reject_duplicate_in_file_and_bad_uploads() throws Exception {
        String token = login();
        String code = "IMP-E2E-SAME-" + System.nanoTime() % 100000;
        String csv = "skuCode,name,weight,turnoverRate,priority\n"
                + code + ",第一行,1,1,1\n"
                + code + ",第二行,1,1,1\n";

        JsonNode data = MAPPER.readTree(upload(token, "/api/v1/import/skus", "skus.csv",
                csv.getBytes(StandardCharsets.UTF_8), null).getBody()).get("data");
        assertThat(data.get("inserted").asInt()).isEqualTo(1);
        assertThat(data.get("failed").asInt()).isEqualTo(1);
        assertThat(data.get("errors").get(0).get("line").asInt()).isEqualTo(3);
        assertThat(data.get("errors").get(0).get("message").asText()).contains("文件内编码重复");

        ResponseEntity<String> badExtension = upload(token, "/api/v1/import/skus", "skus.pdf",
                "x".getBytes(StandardCharsets.UTF_8), null);
        assertThat(badExtension.getStatusCode().value()).isEqualTo(400);
        assertThat(MAPPER.readTree(badExtension.getBody()).get("message").asText())
                .contains("不支持的文件格式");

        ResponseEntity<String> empty = upload(token, "/api/v1/import/skus", "skus.csv", new byte[0], null);
        assertThat(empty.getStatusCode().value()).isEqualTo(400);
        assertThat(MAPPER.readTree(empty.getBody()).get("code").asInt()).isEqualTo(40001);
    }

    @Test
    @DisplayName("API-038：订单导入用 skuCode 定位货物，SKU 不存在与订单号重复逐行报错")
    void should_import_orders_resolving_sku_code() throws Exception {
        String token = login();
        String skuCode = "IMP-E2E-ORD-SKU-" + System.nanoTime() % 100000;
        upload(token, "/api/v1/import/skus", "skus.csv",
                ("skuCode,name,weight,turnoverRate,priority\n" + skuCode + ",订单用货物,1,1,1\n")
                        .getBytes(StandardCharsets.UTF_8), null);

        String orderA = "IMP-E2E-SO-" + System.nanoTime() % 100000 + "-A";
        String orderB = "IMP-E2E-SO-" + System.nanoTime() % 100000 + "-B";
        String csv = "orderNo,skuCode,quantity,priority,placedAt,status,remark\n"
                + orderA + "," + skuCode + ",10,5,2026-09-10T09:00:00,pending,imp-e2e\n"
                + orderB + ",NO-SUCH-SKU,5,3,,,imp-e2e\n"
                + "IMP-E2E-SO-BAD," + skuCode + ",0,3,,,imp-e2e\n";

        ResponseEntity<String> response = upload(token, "/api/v1/import/orders", "orders.csv",
                csv.getBytes(StandardCharsets.UTF_8), null);

        JsonNode data = MAPPER.readTree(response.getBody()).get("data");
        assertThat(data.get("total").asInt()).isEqualTo(3);
        assertThat(data.get("inserted").asInt()).isEqualTo(1);
        assertThat(data.get("failed").asInt()).isEqualTo(2);
        assertThat(data.get("errors").get(0).get("column").asText()).isEqualTo("skuCode");
        assertThat(data.get("errors").get(0).get("message").asText()).contains("SKU 不存在");
        assertThat(data.get("errors").get(1).get("column").asText()).isEqualTo("quantity");

        // 已入库订单：数量/优先级/时间正确，且 skuId 由 skuCode 解析而来
        java.util.Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT quantity, priority, status, sku_id FROM orders WHERE order_no = ?", orderA);
        assertThat(((Number) row.get("quantity")).intValue()).isEqualTo(10);
        assertThat(((Number) row.get("priority")).intValue()).isEqualTo(5);
        assertThat(row.get("status")).isEqualTo("pending");
        assertThat(((Number) row.get("sku_id")).longValue()).isEqualTo(
                jdbcTemplate.queryForObject("SELECT id FROM skus WHERE code = ?", Long.class, skuCode));
    }

    // ------------------------------------------------------------ 导出

    @Test
    @DisplayName("API-039：导出 CSV 带 UTF-8 BOM、RFC 4180 转义，中文回读不乱码")
    void should_export_skus_csv_with_bom_and_escaped_chinese() throws Exception {
        String token = login();
        String code = "IMP-E2E-EXP-" + System.nanoTime() % 100000;
        upload(token, "/api/v1/import/skus", "skus.csv",
                ("skuCode,name,weight,turnoverRate,priority,category,length,width,height,remark\n"
                        + code + ",\"带,逗号与\"\"引号\"\"的名称\",50,0.9,5,电子,30,20,10,\"备注,含逗号\"\n")
                        .getBytes(StandardCharsets.UTF_8), null);

        HttpResponse<byte[]> response = download(token, "/api/v1/export/skus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Disposition").orElse(""))
                .contains("attachment").contains(".csv");
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("text/csv");

        byte[] body = response.body();
        // 前 3 字节应为 UTF-8 BOM（EF BB BF），否则 Excel 打开中文乱码
        assertThat(body[0] & 0xFF).isEqualTo(0xEF);
        assertThat(body[1] & 0xFF).isEqualTo(0xBB);
        assertThat(body[2] & 0xFF).isEqualTo(0xBF);

        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).contains("skuCode").contains("turnoverRate");
        // 中文按 UTF-8 正确编码
        assertThat(csv).contains(code);
        assertThat(csv).contains("电子");
        // RFC 4180：含逗号/引号的字段被包裹并转义
        assertThat(csv).contains("\"带,逗号与\"\"引号\"\"的名称\"");
        assertThat(csv).contains("\"备注,含逗号\"");
        // CRLF 行尾
        assertThat(csv).contains("\r\n");
        assertThat(csv).doesNotContain("\n\r");
    }

    @Test
    @DisplayName("API-039/040：导出 JSON 格式与接口字段名一致，可被解析")
    void should_export_json() throws Exception {
        String token = login();
        String code = "IMP-E2E-JSON-" + System.nanoTime() % 100000;
        upload(token, "/api/v1/import/skus", "skus.csv",
                ("skuCode,name,weight,turnoverRate,priority,category,length,width,height\n"
                        + code + ",JSON 测试,3.5,0.25,2,五金,4,5,6\n")
                        .getBytes(StandardCharsets.UTF_8), null);

        HttpResponse<byte[]> skuJsonResponse = download(token, "/api/v1/export/skus?format=json");
        assertThat(skuJsonResponse.statusCode()).isEqualTo(200);
        assertThat(skuJsonResponse.headers().firstValue("Content-Disposition").orElse(""))
                .contains(".json");

        JsonNode skus = MAPPER.readTree(new String(skuJsonResponse.body(), StandardCharsets.UTF_8));
        JsonNode target = null;
        for (JsonNode node : skus) {
            if (code.equals(node.get("skuCode").asText())) {
                target = node;
            }
        }
        assertThat(target).as("导出内容应包含刚导入的 SKU").isNotNull();
        assertThat(target.get("name").asText()).isEqualTo("JSON 测试");
        assertThat(target.get("weight").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("3.5"));
        assertThat(target.get("turnoverRate").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("0.25"));
        assertThat(target.get("size").get("length").asInt()).isEqualTo(4);

        HttpResponse<byte[]> orderJsonResponse = download(token, "/api/v1/export/orders?format=json");
        assertThat(orderJsonResponse.statusCode()).isEqualTo(200);
        JsonNode orders = MAPPER.readTree(new String(orderJsonResponse.body(), StandardCharsets.UTF_8));
        assertThat(orders.isArray()).isTrue();
        for (JsonNode node : orders) {
            assertThat(node.has("orderNo")).isTrue();
            assertThat(node.has("skuCode")).isTrue();
            assertThat(node.has("placedAt")).isTrue();
        }
    }

    @Test
    @DisplayName("往返一致：导出的 CSV 可被再次导入（幂等：全部跳过）")
    void should_round_trip_exported_csv() throws Exception {
        String token = login();

        HttpResponse<byte[]> exported = download(token, "/api/v1/export/skus");
        String csv = new String(exported.body(), StandardCharsets.UTF_8);

        // 把导出文件原样重新导入：因编码已存在且策略为默认 skip，应全部跳过、零失败
        ResponseEntity<String> reimported = upload(token, "/api/v1/import/skus", "skus.csv",
                csv.getBytes(StandardCharsets.UTF_8), null);

        assertThat(reimported.getStatusCode().value()).as(reimported.getBody()).isEqualTo(200);
        JsonNode data = MAPPER.readTree(reimported.getBody()).get("data");
        assertThat(data.get("failed").asInt()).as("导出文件应能被自己的解析器读回：%s", reimported.getBody())
                .isZero();
        assertThat(data.get("inserted").asInt()).isZero();
        assertThat(data.get("skipped").asInt()).isEqualTo(data.get("total").asInt());
    }

    @Test
    @DisplayName("权限边界：导入需 sku:manage（viewer 403）、导出需 sim:view（四角色均可）")
    void should_enforce_import_export_permissions() throws Exception {
        String adminToken = login();
        String viewerToken = loginAs("viewer");
        String analystToken = loginAs("analyst");
        String operatorToken = loginAs("operator");

        byte[] csv = "skuCode,name,weight,turnoverRate,priority\nIMP-E2E-PERM,权限,1,1,1\n"
                .getBytes(StandardCharsets.UTF_8);

        // 导入：admin 与 operator 允许（200/业务错误另计），analyst 与 viewer 403
        assertThat(upload(adminToken, "/api/v1/import/skus", "skus.csv", csv, null)
                .getStatusCode().value()).isEqualTo(200);
        assertThat(upload(operatorToken, "/api/v1/import/skus", "skus.csv", csv, null)
                .getStatusCode().value()).isEqualTo(200);
        for (String token : java.util.List.of(analystToken, viewerToken)) {
            ResponseEntity<String> forbidden = upload(token, "/api/v1/import/skus", "skus.csv", csv, null);
            assertThat(forbidden.getStatusCode().value())
                    .as("导入应 403：%s", forbidden.getBody()).isEqualTo(403);
            assertThat(MAPPER.readTree(forbidden.getBody()).get("code").asInt()).isEqualTo(40301);
        }

        // 导出：四种角色都可（sim:view）
        for (String token : java.util.List.of(adminToken, operatorToken, analystToken, viewerToken)) {
            assertThat(download(token, "/api/v1/export/skus").statusCode()).isEqualTo(200);
            assertThat(download(token, "/api/v1/export/orders").statusCode()).isEqualTo(200);
        }

        // 未登录 401
        HttpRequest anonymous = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/export/skus"))
                .timeout(Duration.ofSeconds(20)).GET().build();
        assertThat(HTTP.send(anonymous, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    }

    /**
     * 以指定账号登录。
     *
     * @param account 账号
     * @return JWT
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断
     */
    private String loginAs(String account) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/auth/login"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"account\":\"" + account + "\",\"password\":\"admin123\"}",
                        StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(
                StandardCharsets.UTF_8));
        assertThat(response.statusCode()).as("登录 %s 失败：%s", account, response.body()).isEqualTo(200);
        return MAPPER.readTree(response.body()).get("data").get("token").asText();
    }

    /**
     * 统计 SKU 是否存在。
     *
     * @param code SKU 编码
     * @return 数量
     */
    private int countSku(String code) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM skus WHERE code = ?", Integer.class, code);
        return count == null ? 0 : count;
    }
}
