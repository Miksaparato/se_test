package com.wms.data.importexport;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CSV 解析器（A-B4）。
 *
 * <p>由 commons-csv 处理带引号的字段、字段内逗号、字段内换行与转义双引号（RFC 4180），
 * 无需手写状态机。另外自动跳过 **UTF-8 BOM**（Excel 另存为 CSV 会带 BOM，
 * 不处理会导致首列列名变成 {@code \ufeffskuCode} 而匹配不到）。
 *
 * @author a
 */
@Component
public class CsvTabularParser implements TabularParser {

    /** UTF-8 BOM 字符。 */
    private static final String BOM = "\ufeff";

    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".csv") || lower.endsWith(".txt");
    }

    @Override
    public List<Row> parse(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .build();
            try (CSVParser parser = CSVParser.parse(reader, format)) {
                Map<String, Integer> headerIndex = parser.getHeaderMap();
                if (headerIndex == null || headerIndex.isEmpty()) {
                    throw new IOException("CSV 文件缺少表头行");
                }
                // 表头顺序即列索引顺序；同时剥掉首列的 BOM
                List<String> headers = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : headerIndex.entrySet()) {
                    String header = entry.getKey();
                    if (headers.isEmpty() && header.startsWith(BOM)) {
                        header = header.substring(1);
                    }
                    headers.add(header);
                }

                List<Row> rows = new ArrayList<>();
                for (CSVRecord record : parser) {
                    Map<String, String> values = new LinkedHashMap<>();
                    for (String header : headers) {
                        Integer index = headerIndex.get(header);
                        if (index == null && headerIndex.containsKey(BOM + header)) {
                            index = headerIndex.get(BOM + header);
                        }
                        String raw = index != null && index < record.size() ? record.get(index) : null;
                        if (raw != null && raw.startsWith(BOM)) {
                            raw = raw.substring(1);
                        }
                        // 归一化「空」：空串与全空白一律为 null，避免下游出现 "" 与 null 两种空值
                        values.put(header, raw == null || raw.isBlank() ? null : raw.trim());
                    }
                    rows.add(new Row((int) record.getRecordNumber() + 1, values));
                }
                return rows;
            }
        }
    }
}
