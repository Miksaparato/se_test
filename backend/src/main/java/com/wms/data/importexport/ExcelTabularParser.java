package com.wms.data.importexport;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Excel 解析器（A-B4）：支持 {@code .xlsx}（XSSF）与 {@code .xls}（HSSF）。
 *
 * <p>数值单元格用 {@link DataFormatter} 统一转文本，避免 {@code 50.0} 这类科学计数/浮点尾巴；
 * 表头取第一行，列名与 CSV 一致，因此两种格式可共用同一套导入校验逻辑。
 *
 * @author a
 */
@Component
public class ExcelTabularParser implements TabularParser {

    private final DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);

    @Override
    public boolean supports(String filename) {
        if (filename == null) {
            return false;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    @Override
    public List<Row> parse(InputStream inputStream) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new IOException("Excel 文件没有可读取的工作表内容");
            }
            org.apache.poi.ss.usermodel.Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IOException("Excel 文件缺少表头行");
            }
            Map<Integer, String> headers = new LinkedHashMap<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                String header = cellText(headerRow, i);
                if (header != null && !header.isBlank()) {
                    headers.put(i, header.trim());
                }
            }
            if (headers.isEmpty()) {
                throw new IOException("Excel 文件缺少表头行");
            }

            List<Row> rows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (Map.Entry<Integer, String> entry : headers.entrySet()) {
                    values.put(entry.getValue(), cellText(row, entry.getKey()));
                }
                // 行号与用户在 Excel 中看到的一致（1 基）
                rows.add(new Row(rowIndex + 1, values));
            }
            return rows;
        }
    }

    /**
     * 读取单元格文本。
     *
     * @param row  行
     * @param index 列索引
     * @return 文本值，空单元格返回 null
     */
    private String cellText(org.apache.poi.ss.usermodel.Row row, int index) {
        Cell cell = row.getCell(index, MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.FORMULA) {
            return dataFormatter.formatCellValue(cell,
                    cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator());
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            // 数值列去掉无意义的浮点尾巴：50.000 → 50，0.9 → 0.9
            double numeric = cell.getNumericCellValue();
            return new BigDecimal(String.valueOf(numeric)).stripTrailingZeros().toPlainString();
        }
        String text = dataFormatter.formatCellValue(cell);
        return text == null || text.isBlank() ? null : text.trim();
    }
}
