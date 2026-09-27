package com.wms.data.importexport;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.data.importexport.vo.ImportResultVO;
import com.wms.data.importexport.vo.ImportResultVO.ImportError;
import com.wms.data.order.OrderService;
import com.wms.data.sku.SkuService;
import com.wms.domain.entity.Order;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.type.SkuSize;
import com.wms.domain.mapper.OrderMapper;
import com.wms.domain.mapper.SkuMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据导入导出服务（A-B4），对应 API-037 ~ API-040、FR-1.2、NFR-7。
 *
 * <p>导入策略：
 * <ul>
 *   <li>合法行入库、非法行**只报告不阻断**，逐行返回「物理行号 + 列名 + 原因」；</li>
 *   <li>重复编码按 {@code strategy} 处理：{@code skip} 跳过 / {@code update} 覆盖更新；</li>
 *   <li>写入走 {@link SkuService} / {@link OrderService}，保证与单条接口共用同一套校验。</li>
 * </ul>
 *
 * <p>导出策略：CSV 带 **UTF-8 BOM**（否则 Excel 打开中文乱码）、CRLF 行尾（Excel 兼容）、
 * 字段按 RFC 4180 转义。
 *
 * @author a
 */
@Service
public class ImportExportService {

    private static final Logger log = LoggerFactory.getLogger(ImportExportService.class);

    /** 重复编码处理策略：跳过。 */
    public static final String STRATEGY_SKIP = "skip";

    /** 重复编码处理策略：更新。 */
    public static final String STRATEGY_UPDATE = "update";

    /** UTF-8 BOM，供 Excel 正确识别中文。 */
    private static final String UTF8_BOM = "\ufeff";

    /** 导出时间格式（ISO 8601 UTC，与接口约定一致）。 */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /** 导入接受的时间格式（兼容常见写法）。 */
    private static final List<DateTimeFormatter> IMPORT_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME);

    /** SKU 导入表头。 */
    private static final List<String> SKU_HEADERS = List.of(
            "skuCode", "name", "weight", "turnoverRate", "priority", "category",
            "length", "width", "height", "remark");

    /** 订单导入表头。 */
    private static final List<String> ORDER_HEADERS = List.of(
            "orderNo", "skuCode", "quantity", "priority", "placedAt", "status", "remark");

    private final TabularFileReader fileReader;

    private final SkuService skuService;

    private final OrderService orderService;

    private final SkuMapper skuMapper;

    private final OrderMapper orderMapper;

    /**
     * 构造导入导出服务。
     *
     * @param fileReader   文件解析调度器
     * @param skuService   货物服务
     * @param orderService 订单服务
     * @param skuMapper    货物 Mapper（批量写入）
     * @param orderMapper  订单 Mapper（批量写入）
     */
    public ImportExportService(TabularFileReader fileReader,
                               SkuService skuService,
                               OrderService orderService,
                               SkuMapper skuMapper,
                               OrderMapper orderMapper) {
        this.fileReader = fileReader;
        this.skuService = skuService;
        this.orderService = orderService;
        this.skuMapper = skuMapper;
        this.orderMapper = orderMapper;
    }

    // ------------------------------------------------------------------ 导入

    /**
     * 批量导入 SKU（API-037）。
     *
     * @param file     上传文件（CSV / Excel）
     * @param strategy 重复编码策略：skip / update
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importSkus(MultipartFile file, String strategy) {
        String effectiveStrategy = normalizeStrategy(strategy);
        List<TabularParser.Row> rows = fileReader.read(file).stream()
                .filter(row -> !row.isBlank())
                .toList();

        List<ImportError> errors = new ArrayList<>();
        List<Sku> toInsert = new ArrayList<>();
        List<Sku> toUpdate = new ArrayList<>();
        int skipped = 0;
        // 文件内查重：同一文件里重复出现的编码，后者视为重复
        Map<String, Integer> seenCodes = new HashMap<>();

        for (TabularParser.Row row : rows) {
            String code = row.get("skuCode");
            if (!StringUtils.hasText(code)) {
                errors.add(new ImportError(row.lineNumber(), "skuCode", "SKU 编码不能为空"));
                continue;
            }
            Integer firstLine = seenCodes.putIfAbsent(code, row.lineNumber());
            if (firstLine != null) {
                errors.add(new ImportError(row.lineNumber(), "skuCode",
                        "文件内编码重复，已在第 " + firstLine + " 行出现：" + code));
                continue;
            }

            Sku parsed = new Sku();
            parsed.setCode(code);
            String name = row.get("name");
            if (!StringUtils.hasText(name)) {
                errors.add(new ImportError(row.lineNumber(), "name", "货物名称不能为空"));
                continue;
            }
            parsed.setName(name);

            BigDecimal weight = parseDecimal(row, "weight", errors);
            if (weight == null || weight.signum() < 0) {
                if (weight != null) {
                    errors.add(new ImportError(row.lineNumber(), "weight", "重量不能为负数"));
                }
                continue;
            }
            parsed.setWeight(weight);

            BigDecimal turnoverRate = parseDecimal(row, "turnoverRate", errors);
            if (turnoverRate == null || turnoverRate.signum() < 0) {
                if (turnoverRate != null) {
                    errors.add(new ImportError(row.lineNumber(), "turnoverRate", "周转频次不能为负数"));
                }
                continue;
            }
            parsed.setTurnoverRate(turnoverRate);

            Integer priority = parseInteger(row, "priority", errors);
            if (priority == null || priority < 1 || priority > 5) {
                if (priority != null) {
                    errors.add(new ImportError(row.lineNumber(), "priority", "出库优先级须为 1~5"));
                }
                continue;
            }
            parsed.setPriority(priority);

            parsed.setCategory(row.get("category"));
            parsed.setRemark(row.get("remark"));

            SkuSize size = parseSize(row, errors);
            if (size == SkuSizeHolder.INVALID) {
                continue;
            }
            parsed.setSize(size);

            Sku existing = skuService.findByCode(code);
            if (existing == null) {
                toInsert.add(parsed);
            } else if (STRATEGY_UPDATE.equals(effectiveStrategy)) {
                parsed.setId(existing.getId());
                toUpdate.add(parsed);
            } else {
                skipped++;
            }
        }

        for (Sku sku : toInsert) {
            skuMapper.insert(sku);
        }
        for (Sku sku : toUpdate) {
            skuMapper.updateById(sku);
        }

        ImportResultVO result = ImportResultVO.of(rows.size(), toInsert.size(), toUpdate.size(),
                skipped, errors);
        log.info("导入 SKU 完成 总行数={} 新增={} 更新={} 跳过={} 失败={} 策略={}",
                result.total(), result.inserted(), result.updated(), result.skipped(),
                result.failed(), effectiveStrategy);
        return result;
    }

    /**
     * 批量导入订单（API-038）。
     *
     * <p>订单文件用 {@code skuCode} 指定货物（比 id 更可读、可跨环境迁移），
     * 由服务端解析为 {@code skuId}。
     *
     * @param file     上传文件
     * @param strategy 重复订单号策略：skip / update
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportResultVO importOrders(MultipartFile file, String strategy) {
        String effectiveStrategy = normalizeStrategy(strategy);
        List<TabularParser.Row> rows = fileReader.read(file).stream()
                .filter(row -> !row.isBlank())
                .toList();

        // 一次查出全部 SKU 编码映射，避免逐行查询（《代码规范》4.4）
        Map<String, Long> skuIdByCode = new HashMap<>();
        for (Sku sku : skuMapper.selectList(Wrappers.<Sku>lambdaQuery())) {
            skuIdByCode.put(sku.getCode(), sku.getId());
        }

        List<ImportError> errors = new ArrayList<>();
        List<Order> toInsert = new ArrayList<>();
        List<Order> toUpdate = new ArrayList<>();
        int skipped = 0;
        Map<String, Integer> seenOrderNos = new HashMap<>();

        for (TabularParser.Row row : rows) {
            Integer quantity = parseInteger(row, "quantity", errors);
            if (quantity == null || quantity < 1) {
                if (quantity != null) {
                    errors.add(new ImportError(row.lineNumber(), "quantity", "出库数量最小为 1"));
                }
                continue;
            }
            Integer priority = parseInteger(row, "priority", errors);
            if (priority == null || priority < 1 || priority > 5) {
                if (priority != null) {
                    errors.add(new ImportError(row.lineNumber(), "priority", "订单优先级须为 1~5"));
                }
                continue;
            }

            String skuCode = row.get("skuCode");
            if (!StringUtils.hasText(skuCode)) {
                errors.add(new ImportError(row.lineNumber(), "skuCode", "SKU 编码不能为空"));
                continue;
            }
            Long skuId = skuIdByCode.get(skuCode);
            if (skuId == null) {
                errors.add(new ImportError(row.lineNumber(), "skuCode", "SKU 不存在：" + skuCode));
                continue;
            }

            LocalDateTime placedAt = parseTime(row, "placedAt", errors);
            if (placedAt == null && StringUtils.hasText(row.get("placedAt"))) {
                continue;
            }

            String orderNo = row.get("orderNo");
            if (!StringUtils.hasText(orderNo)) {
                errors.add(new ImportError(row.lineNumber(), "orderNo", "订单号不能为空"));
                continue;
            }
            Integer firstLine = seenOrderNos.putIfAbsent(orderNo, row.lineNumber());
            if (firstLine != null) {
                errors.add(new ImportError(row.lineNumber(), "orderNo",
                        "文件内订单号重复，已在第 " + firstLine + " 行出现：" + orderNo));
                continue;
            }

            String status = row.getOrDefault("status", OrderService.STATUS_PENDING);
            if (!List.of(OrderService.STATUS_PENDING, OrderService.STATUS_PICKING,
                    OrderService.STATUS_COMPLETED, OrderService.STATUS_CANCELLED).contains(status)) {
                errors.add(new ImportError(row.lineNumber(), "status",
                        "订单状态只能是 pending、picking、completed 或 cancelled"));
                continue;
            }

            Order parsed = new Order();
            parsed.setOrderNo(orderNo);
            parsed.setSkuId(skuId);
            parsed.setQuantity(quantity);
            parsed.setPriority(priority);
            parsed.setPlacedAt(placedAt == null ? LocalDateTime.now() : placedAt);
            parsed.setStatus(status);
            parsed.setRemark(row.get("remark"));

            Order existing = orderService.findByOrderNo(orderNo);
            if (existing == null) {
                toInsert.add(parsed);
            } else if (STRATEGY_UPDATE.equals(effectiveStrategy)) {
                parsed.setId(existing.getId());
                toUpdate.add(parsed);
            } else {
                skipped++;
            }
        }

        for (Order order : toInsert) {
            orderMapper.insert(order);
        }
        for (Order order : toUpdate) {
            orderMapper.updateById(order);
        }

        ImportResultVO result = ImportResultVO.of(rows.size(), toInsert.size(), toUpdate.size(),
                skipped, errors);
        log.info("导入订单完成 总行数={} 新增={} 更新={} 跳过={} 失败={} 策略={}",
                result.total(), result.inserted(), result.updated(), result.skipped(),
                result.failed(), effectiveStrategy);
        return result;
    }

    // ------------------------------------------------------------------ 导出

    /**
     * 导出 SKU（API-039）。
     *
     * @param format 导出格式：csv / json
     * @return 文件字节内容
     */
    public byte[] exportSkus(String format) {
        List<Sku> skus = skuMapper.selectList(Wrappers.<Sku>lambdaQuery().orderByAsc(Sku::getId));
        if (isJson(format)) {
            return buildSkuJson(skus).getBytes(StandardCharsets.UTF_8);
        }
        List<List<String>> rows = new ArrayList<>();
        for (Sku sku : skus) {
            SkuSize size = sku.getSize();
            rows.add(List.of(
                    nullToEmpty(sku.getCode()),
                    nullToEmpty(sku.getName()),
                    plain(sku.getWeight()),
                    plain(sku.getTurnoverRate()),
                    sku.getPriority() == null ? "" : String.valueOf(sku.getPriority()),
                    nullToEmpty(sku.getCategory()),
                    size == null || size.length() == null ? "" : String.valueOf(size.length()),
                    size == null || size.width() == null ? "" : String.valueOf(size.width()),
                    size == null || size.height() == null ? "" : String.valueOf(size.height()),
                    nullToEmpty(sku.getRemark())));
        }
        return buildCsv(SKU_HEADERS, rows).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 导出订单（API-040）。
     *
     * @param format 导出格式：csv / json
     * @return 文件字节内容
     */
    public byte[] exportOrders(String format) {
        List<Order> orders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .orderByAsc(Order::getId));
        Map<Long, String> skuCodes = skuService.loadCodesByIds(orders.stream()
                .map(Order::getSkuId).toList());

        if (isJson(format)) {
            return buildOrderJson(orders, skuCodes).getBytes(StandardCharsets.UTF_8);
        }
        List<List<String>> rows = new ArrayList<>();
        for (Order order : orders) {
            rows.add(List.of(
                    nullToEmpty(order.getOrderNo()),
                    nullToEmpty(skuCodes.get(order.getSkuId())),
                    order.getQuantity() == null ? "" : String.valueOf(order.getQuantity()),
                    order.getPriority() == null ? "" : String.valueOf(order.getPriority()),
                    order.getPlacedAt() == null ? "" : TIME_FORMAT.format(order.getPlacedAt()),
                    nullToEmpty(order.getStatus()),
                    nullToEmpty(order.getRemark())));
        }
        return buildCsv(ORDER_HEADERS, rows).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成 CSV 文本：带 UTF-8 BOM、CRLF 行尾、RFC 4180 转义。
     *
     * @param headers 表头
     * @param rows    数据行
     * @return CSV 文本
     */
    private String buildCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder builder = new StringBuilder(UTF8_BOM);
        builder.append(joinCsvRow(headers)).append("\r\n");
        for (List<String> row : rows) {
            builder.append(joinCsvRow(row)).append("\r\n");
        }
        return builder.toString();
    }

    /**
     * 拼接一行 CSV。
     *
     * @param values 单元格值
     * @return 一行文本
     */
    private String joinCsvRow(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escapeCsv(values.get(i)));
        }
        return builder.toString();
    }

    /**
     * 按 RFC 4180 转义单元格：含逗号、引号、换行时用双引号包裹并转义内部引号。
     *
     * @param value 原值
     * @return 转义后的值
     */
    private String escapeCsv(String value) {
        String safe = value == null ? "" : value;
        boolean needQuote = safe.contains(",") || safe.contains("\"")
                || safe.contains("\n") || safe.contains("\r");
        if (!needQuote) {
            return safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    /**
     * 判断是否导出 JSON。
     *
     * @param format 格式参数
     * @return JSON 返回 true
     */
    private boolean isJson(String format) {
        return "json".equalsIgnoreCase(format == null ? "" : format.trim());
    }

    /**
     * 构造 SKU 的 JSON 导出内容（字段名与接口一致）。
     *
     * @param skus 货物列表
     * @return JSON 文本
     */
    private String buildSkuJson(List<Sku> skus) {
        StringBuilder builder = new StringBuilder("[\n");
        for (int i = 0; i < skus.size(); i++) {
            Sku sku = skus.get(i);
            SkuSize size = sku.getSize();
            builder.append("  {\"skuCode\":").append(json(sku.getCode()))
                    .append(",\"name\":").append(json(sku.getName()))
                    .append(",\"weight\":").append(plain(sku.getWeight()))
                    .append(",\"turnoverRate\":").append(plain(sku.getTurnoverRate()))
                    .append(",\"priority\":").append(sku.getPriority() == null ? "null" : sku.getPriority())
                    .append(",\"category\":").append(json(sku.getCategory()))
                    .append(",\"size\":").append(size == null ? "null" : String.format(
                            "{\"length\":%d,\"width\":%d,\"height\":%d}",
                            size.length() == null ? 0 : size.length(),
                            size.width() == null ? 0 : size.width(),
                            size.height() == null ? 0 : size.height()))
                    .append(",\"remark\":").append(json(sku.getRemark()))
                    .append("}");
            if (i < skus.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        return builder.append("]\n").toString();
    }

    /**
     * 构造订单的 JSON 导出内容（字段名与接口一致）。
     *
     * @param orders   订单列表
     * @param skuCodes 货物编码映射
     * @return JSON 文本
     */
    private String buildOrderJson(List<Order> orders, Map<Long, String> skuCodes) {
        StringBuilder builder = new StringBuilder("[\n");
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            builder.append("  {\"orderNo\":").append(json(order.getOrderNo()))
                    .append(",\"skuCode\":").append(json(skuCodes.get(order.getSkuId())))
                    .append(",\"quantity\":").append(order.getQuantity() == null ? "null" : order.getQuantity())
                    .append(",\"priority\":").append(order.getPriority() == null ? "null" : order.getPriority())
                    .append(",\"placedAt\":")
                    .append(order.getPlacedAt() == null ? "null" : json(TIME_FORMAT.format(order.getPlacedAt())))
                    .append(",\"status\":").append(json(order.getStatus()))
                    .append(",\"remark\":").append(json(order.getRemark()))
                    .append("}");
            if (i < orders.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        return builder.append("]\n").toString();
    }

    /**
     * JSON 字符串转义。
     *
     * @param value 原值
     * @return JSON 字符串字面量
     */
    private String json(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder("\"");
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    // ------------------------------------------------------------------ 解析辅助

    /**
     * 规整重复策略参数。
     *
     * @param strategy 原始策略
     * @return skip 或 update
     */
    private String normalizeStrategy(String strategy) {
        if (!StringUtils.hasText(strategy)) {
            return STRATEGY_SKIP;
        }
        String normalized = strategy.trim().toLowerCase(java.util.Locale.ROOT);
        if (!STRATEGY_SKIP.equals(normalized) && !STRATEGY_UPDATE.equals(normalized)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "重复处理策略只能是 skip 或 update，实际：" + strategy);
        }
        return normalized;
    }

    /**
     * 解析小数单元格。
     *
     * @param row    数据行
     * @param column 列名
     * @param errors 错误收集器
     * @return 解析结果，失败时返回 null 并写入错误
     */
    private BigDecimal parseDecimal(TabularParser.Row row, String column, List<ImportError> errors) {
        String raw = row.get(column);
        if (!StringUtils.hasText(raw)) {
            errors.add(new ImportError(row.lineNumber(), column, column + " 不能为空"));
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            errors.add(new ImportError(row.lineNumber(), column, column + " 不是合法数字：" + raw));
            return null;
        }
    }

    /**
     * 解析整数单元格。
     *
     * @param row    数据行
     * @param column 列名
     * @param errors 错误收集器
     * @return 解析结果，失败时返回 null 并写入错误
     */
    private Integer parseInteger(TabularParser.Row row, String column, List<ImportError> errors) {
        String raw = row.get(column);
        if (!StringUtils.hasText(raw)) {
            errors.add(new ImportError(row.lineNumber(), column, column + " 不能为空"));
            return null;
        }
        try {
            return new BigDecimal(raw).intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            errors.add(new ImportError(row.lineNumber(), column, column + " 不是合法整数：" + raw));
            return null;
        }
    }

    /**
     * 解析时间单元格，兼容多种常见写法。
     *
     * @param row    数据行
     * @param column 列名
     * @param errors 错误收集器
     * @return 解析结果；列不存在或为空时返回 null（不报错，交由调用方取当前时间）
     */
    private LocalDateTime parseTime(TabularParser.Row row, String column, List<ImportError> errors) {
        String raw = row.get(column);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        for (DateTimeFormatter formatter : IMPORT_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        try {
            // 纯日期写法按当天 0 点处理
            return LocalDate.parse(raw).atStartOfDay();
        } catch (DateTimeParseException ex) {
            errors.add(new ImportError(row.lineNumber(), column,
                    column + " 时间格式不正确（支持 yyyy-MM-dd 或 yyyy-MM-ddTHH:mm:ss）：" + raw));
            return null;
        }
    }

    /**
     * 解析尺寸三列（length / width / height）。
     *
     * @param row    数据行
     * @param errors 错误收集器
     * @return 尺寸对象；三列全空时返回 null；非法时返回 {@link SkuSizeHolder#INVALID}
     */
    private SkuSize parseSize(TabularParser.Row row, List<ImportError> errors) {
        String length = row.get("length");
        String width = row.get("width");
        String height = row.get("height");
        if (!StringUtils.hasText(length) && !StringUtils.hasText(width) && !StringUtils.hasText(height)) {
            return null;
        }
        Integer parsedLength = parseSizePart(row, "length", length, errors);
        Integer parsedWidth = parseSizePart(row, "width", width, errors);
        Integer parsedHeight = parseSizePart(row, "height", height, errors);
        if (parsedLength == null || parsedWidth == null || parsedHeight == null) {
            return SkuSizeHolder.INVALID;
        }
        return new SkuSize(parsedLength, parsedWidth, parsedHeight);
    }

    /**
     * 解析尺寸的单个维度。
     *
     * @param row    数据行
     * @param column 列名
     * @param raw    原始值
     * @param errors 错误收集器
     * @return 维度值
     */
    private Integer parseSizePart(TabularParser.Row row, String column, String raw,
                                  List<ImportError> errors) {
        if (!StringUtils.hasText(raw)) {
            errors.add(new ImportError(row.lineNumber(), column,
                    "尺寸要么三列都填，要么都不填（缺少 " + column + "）"));
            return null;
        }
        try {
            int value = new BigDecimal(raw).intValueExact();
            if (value < 0) {
                errors.add(new ImportError(row.lineNumber(), column, "尺寸不能为负数"));
                return null;
            }
            return value;
        } catch (ArithmeticException | NumberFormatException ex) {
            errors.add(new ImportError(row.lineNumber(), column, column + " 不是合法整数：" + raw));
            return null;
        }
    }

    /**
     * 数值输出：去掉无意义的尾随零（50.000 → 50，0.9 → 0.9）。
     *
     * @param value 数值
     * @return 文本
     */
    private String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * null 转空串。
     *
     * @param value 原值
     * @return 非 null 文本
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 尺寸解析失败标记。
     */
    private static final class SkuSizeHolder {

        /** 非法尺寸哨兵值。 */
        private static final SkuSize INVALID = new SkuSize(-1, -1, -1);

        private SkuSizeHolder() {
        }
    }
}
