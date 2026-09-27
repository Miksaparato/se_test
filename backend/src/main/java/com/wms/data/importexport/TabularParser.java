package com.wms.data.importexport;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 表格文件解析器（A-B4）：把上传文件解析为「行号 + 列名→值」结构。
 *
 * <p>列名取自文件表头，便于按列名取值，避免列顺序变化导致导入错位。
 * 行号从 2 开始计（第 1 行是表头），与用户在 Excel 中看到的行号一致。
 *
 * @author a
 */
public interface TabularParser {

    /**
     * 是否支持该文件名/类型。
     *
     * @param filename 原始文件名
     * @return 支持返回 true
     */
    boolean supports(String filename);

    /**
     * 解析表格。
     *
     * @param inputStream 文件流
     * @return 数据行列表（不含表头）
     * @throws IOException 读取失败或缺少表头
     */
    List<Row> parse(InputStream inputStream) throws IOException;

    /**
     * 一行数据。
     *
     * @param lineNumber 行号（从 2 开始，对应文件中的物理行）
     * @param values     列名 → 单元格文本
     */
    record Row(int lineNumber, Map<String, String> values) {

        /**
         * 取单元格文本。
         *
         * @param column 列名
         * @return 去空白后的值，不存在时返回 null
         */
        public String get(String column) {
            String value = values.get(column);
            return value == null ? null : value.trim();
        }

        /**
         * 取单元格文本，空值时返回默认值。
         *
         * @param column       列名
         * @param defaultValue 默认值
         * @return 单元格值或默认值
         */
        public String getOrDefault(String column, String defaultValue) {
            String value = get(column);
            return value == null || value.isEmpty() ? defaultValue : value;
        }

        /**
         * 判断整行是否为空（全列无值）。
         *
         * @return 空行返回 true
         */
        public boolean isBlank() {
            return values.values().stream().allMatch(value -> value == null || value.isBlank());
        }
    }
}
