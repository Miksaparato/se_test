package com.wms.data.importexport;

import com.wms.common.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CSV 解析器单元测试（a，A-B4 / T-3）：覆盖 RFC 4180 转义、BOM、空行与缺表头等边界。
 *
 * <p>纯 JUnit 5，不启动 Spring 上下文（《代码规范》4.7）。
 *
 * @author a
 */
class CsvTabularParserTest {

    private final CsvTabularParser parser = new CsvTabularParser();

    /**
     * 构造输入流。
     *
     * @param content 文本内容
     * @return 输入流
     */
    private InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("按文件名后缀识别支持的格式")
    void should_support_csv_and_txt_only() {
        assertThat(parser.supports("skus.csv")).isTrue();
        assertThat(parser.supports("SKUS.CSV")).isTrue();
        assertThat(parser.supports("skus.txt")).isTrue();
        assertThat(parser.supports("skus.xlsx")).isFalse();
        assertThat(parser.supports("skus")).isFalse();
        assertThat(parser.supports(null)).isFalse();
    }

    @Test
    @DisplayName("基本解析：按列名取值，行号从 2 开始（与编辑器显示一致）")
    void should_parse_basic_csv_with_column_names() throws IOException {
        List<TabularParser.Row> rows = parser.parse(stream(
                "skuCode,name,weight\n"
                        + "SKU-001,电子元件,50\n"
                        + "SKU-002,螺丝,1.5\n"));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).lineNumber()).isEqualTo(2);
        assertThat(rows.get(0).get("skuCode")).isEqualTo("SKU-001");
        assertThat(rows.get(0).get("weight")).isEqualTo("50");
        assertThat(rows.get(1).lineNumber()).isEqualTo(3);
        assertThat(rows.get(1).get("name")).isEqualTo("螺丝");
    }

    @Test
    @DisplayName("列顺序变化不影响取值（按列名而非列位置）")
    void should_be_order_independent() throws IOException {
        List<TabularParser.Row> rows = parser.parse(stream(
                "name,weight,skuCode\n"
                        + "电子元件,50,SKU-001\n"));

        assertThat(rows.get(0).get("skuCode")).isEqualTo("SKU-001");
        assertThat(rows.get(0).get("name")).isEqualTo("电子元件");
    }

    @Test
    @DisplayName("带引号字段：字段内逗号、双引号、换行均应正确解析")
    void should_parse_quoted_fields_with_comma_quote_and_newline() throws IOException {
        String content = "skuCode,name,remark\n"
                + "SKU-001,\"含,逗号的名字\",\"含\"\"引号\"\"的备注\"\n"
                + "SKU-002,普通,\"第一行\n第二行\"\n";

        List<TabularParser.Row> rows = parser.parse(stream(content));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("name")).isEqualTo("含,逗号的名字");
        assertThat(rows.get(0).get("remark")).isEqualTo("含\"引号\"的备注");
        assertThat(rows.get(1).get("remark")).isEqualTo("第一行\n第二行");
    }

    @Test
    @DisplayName("UTF-8 BOM：首列列名不应被污染为 \\ufeffskuCode")
    void should_strip_utf8_bom_from_header_and_values() throws IOException {
        List<TabularParser.Row> rows = parser.parse(stream(
                "\ufeffskuCode,name\n"
                        + "SKU-001,电子元件\n"));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("skuCode")).as("BOM 应被剥离").isEqualTo("SKU-001");
        assertThat(rows.get(0).values().keySet()).containsExactly("skuCode", "name");
    }

    @Test
    @DisplayName("空行被忽略，空单元格返回 null，getOrDefault 返回默认值")
    void should_ignore_empty_lines_and_handle_blank_cells() throws IOException {
        List<TabularParser.Row> rows = parser.parse(stream(
                "skuCode,name,category\n"
                        + "SKU-001,电子元件,\n"
                        + "\n"
                        + "SKU-002,螺丝,五金\n"));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("category")).isNull();
        assertThat(rows.get(0).getOrDefault("category", "未分类")).isEqualTo("未分类");
        assertThat(rows.get(1).getOrDefault("category", "未分类")).isEqualTo("五金");
    }

    @Test
    @DisplayName("单元格首尾空白被裁剪")
    void should_trim_cell_values() throws IOException {
        List<TabularParser.Row> rows = parser.parse(stream(
                "skuCode, name \n"
                        + "  SKU-001 ,  电子元件  \n"));

        assertThat(rows.get(0).get("skuCode")).isEqualTo("SKU-001");
        assertThat(rows.get(0).get("name")).isEqualTo("电子元件");
    }

    @Test
    @DisplayName("整行为空的判断：isBlank 为 true 时才应被跳过")
    void should_detect_blank_rows() throws IOException {
        List<TabularParser.Row> rows = parser.parse(stream(
                "skuCode,name\n"
                        + ",,\n"
                        + "SKU-001,\n"));

        assertThat(rows.get(0).isBlank()).isTrue();
        assertThat(rows.get(1).isBlank()).isFalse();
    }

    @Test
    @DisplayName("缺少表头时应抛 IOException（由上层转为 40001）")
    void should_fail_when_header_missing() {
        assertThatThrownBy(() -> parser.parse(stream("")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("表头");
    }

    @Test
    @DisplayName("文件解析调度器：不支持的后缀应返回业务错误，空文件同样")
    void should_reject_unsupported_extension_and_empty_file() {
        TabularFileReader reader = new TabularFileReader(List.of(parser));

        assertThatThrownBy(() -> reader.read(new org.springframework.mock.web.MockMultipartFile(
                "file", "skus.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的文件格式");

        assertThatThrownBy(() -> reader.read(new org.springframework.mock.web.MockMultipartFile(
                "file", "skus.csv", "text/csv", new byte[0])))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能为空");
    }
}
