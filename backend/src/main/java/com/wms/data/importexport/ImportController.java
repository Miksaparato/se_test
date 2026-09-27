package com.wms.data.importexport;

import com.wms.common.ApiResponse;
import com.wms.data.importexport.vo.ImportResultVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据导入控制器，对应《接口文档》API-037 / API-038（需 {@code sku:manage}）。
 *
 * <p>请求为 {@code multipart/form-data}，文件字段名 {@code file}。
 * 导入**不会因个别行非法而整体失败**：响应中的 {@code errors} 逐行给出
 * 「行号 + 列名 + 原因」，合法行已入库。
 *
 * @author a
 */
@RestController
@RequestMapping("/api/v1/import")
@PreAuthorize("hasAuthority('sku:manage')")
public class ImportController {

    private final ImportExportService importExportService;

    /**
     * 构造导入控制器。
     *
     * @param importExportService 导入导出服务
     */
    public ImportController(ImportExportService importExportService) {
        this.importExportService = importExportService;
    }

    /**
     * API-037 批量导入 SKU（CSV/Excel）。
     *
     * @param file     上传文件，字段名 {@code file}
     * @param strategy 重复 SKU 编码策略：skip（默认，跳过）/ update（覆盖更新）
     * @return 导入结果（含逐行错误）
     */
    @PostMapping("/skus")
    public ApiResponse<ImportResultVO> importSkus(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String strategy) {
        return ApiResponse.ok(importExportService.importSkus(file, strategy));
    }

    /**
     * API-038 批量导入订单（CSV/Excel）。
     *
     * @param file     上传文件，字段名 {@code file}
     * @param strategy 重复订单号策略：skip（默认）/ update
     * @return 导入结果（含逐行错误）
     */
    @PostMapping("/orders")
    public ApiResponse<ImportResultVO> importOrders(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String strategy) {
        return ApiResponse.ok(importExportService.importOrders(file, strategy));
    }
}
