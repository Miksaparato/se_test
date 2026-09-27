package com.wms.data.importexport;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 数据导出控制器，对应《接口文档》API-039 / API-040（需 {@code sim:view}）。
 *
 * <p>返回**文件流**而非统一 JSON 结构（《接口文档》1.1 已注明"导入导出除外"）：
 * {@code Content-Disposition: attachment} 指定文件名，CSV 带 UTF-8 BOM 以便 Excel 正确显示中文。
 *
 * @author a
 */
@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    /** 文件名时间戳格式。 */
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ImportExportService importExportService;

    /**
     * 构造导出控制器。
     *
     * @param importExportService 导入导出服务
     */
    public ExportController(ImportExportService importExportService) {
        this.importExportService = importExportService;
    }

    /**
     * API-039 导出 SKU（CSV/JSON）。
     *
     * @param format 导出格式：csv（默认）/ json
     * @return 文件下载响应
     */
    @GetMapping("/skus")
    @PreAuthorize("hasAuthority('sim:view')")
    public ResponseEntity<byte[]> exportSkus(@RequestParam(required = false) String format) {
        boolean json = "json".equalsIgnoreCase(format == null ? "" : format.trim());
        byte[] content = importExportService.exportSkus(format);
        return download(content, "skus", json);
    }

    /**
     * API-040 导出订单（CSV/JSON）。
     *
     * @param format 导出格式：csv（默认）/ json
     * @return 文件下载响应
     */
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('sim:view')")
    public ResponseEntity<byte[]> exportOrders(@RequestParam(required = false) String format) {
        boolean json = "json".equalsIgnoreCase(format == null ? "" : format.trim());
        byte[] content = importExportService.exportOrders(format);
        return download(content, "orders", json);
    }

    /**
     * 构造文件下载响应。
     *
     * @param content 文件内容
     * @param prefix  文件名前缀
     * @param json    是否为 JSON 格式
     * @return 响应实体
     */
    private ResponseEntity<byte[]> download(byte[] content, String prefix, boolean json) {
        String extension = json ? "json" : "csv";
        String filename = prefix + "-" + LocalDateTime.now().format(FILE_TIME) + "." + extension;
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(json
                ? new MediaType("application", "json", StandardCharsets.UTF_8)
                : new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(content.length);
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
