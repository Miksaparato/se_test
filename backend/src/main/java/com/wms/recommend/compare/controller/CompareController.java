package com.wms.recommend.compare.controller;

import com.wms.common.ApiResponse;
import com.wms.domain.entity.Plan;
import com.wms.recommend.compare.dto.CompareRequest;
import com.wms.recommend.compare.dto.CompareResult;
import com.wms.recommend.compare.service.CompareService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 方案对比、优化建议与报告接口（API-049 ~ API-053）。
 *
 * <p>权限码按《接口文档》第 11 节的权限列声明，由 a 的鉴权中间件（A-B8）
 * 通过 {@code @PreAuthorize} 强制校验：对比类接口需 {@code compare:view}，
 * 报告导出需 {@code report:export}。
 */
@RestController
@RequestMapping("/api/v1/plans")
public class CompareController {

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    /**
     * API-049 分配方案列表。
     *
     * @param warehouseId 可选，按仓库过滤（方案对比列表的主查询路径，见《数据库设计说明书》5.3）
     * @return 方案列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('compare:view')")
    public ApiResponse<List<Plan>> listPlans(
            @RequestParam(name = "warehouse_id", required = false) Long warehouseId) {
        return ApiResponse.ok(compareService.listPlans(warehouseId));
    }

    /**
     * API-050 方案详情（策略、参数、占用映射、统计）。
     *
     * @param id 方案 id
     * @return 方案详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('compare:view')")
    public ApiResponse<Plan> getPlan(@PathVariable Long id) {
        return ApiResponse.ok(compareService.getPlan(id));
    }

    /**
     * API-051 多方案指标对比。
     *
     * @param req 方案 id 列表
     * @return 对比指标与优化建议
     */
    @PostMapping("/compare")
    @PreAuthorize("hasAuthority('compare:view')")
    public ApiResponse<CompareResult> compare(@Valid @RequestBody CompareRequest req) {
        return ApiResponse.ok(compareService.compare(req.planIds()));
    }

    /**
     * API-052 生成优化建议文字。
     *
     * @param id 基线方案 id
     * @return 建议文字
     */
    @PostMapping("/{id}/suggestions")
    @PreAuthorize("hasAuthority('compare:view')")
    public ApiResponse<String> suggest(@PathVariable Long id) {
        return ApiResponse.ok(compareService.suggest(id));
    }

    /**
     * API-053 导出对比报告（Markdown/CSV）。
     *
     * @param id     基线方案 id
     * @param format 导出格式：md（默认）/ csv
     * @return 报告文件流
     */
    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('report:export')")
    public ResponseEntity<String> export(@PathVariable Long id,
                                         @RequestParam(defaultValue = "md") String format) {
        String report = compareService.export(id, format);
        boolean csv = "csv".equalsIgnoreCase(format);
        MediaType mediaType = csv ? MediaType.parseMediaType("text/csv; charset=UTF-8")
                : MediaType.parseMediaType("text/markdown; charset=UTF-8");
        String filename = "plan-" + id + (csv ? ".csv" : ".md");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(report);
    }
}
