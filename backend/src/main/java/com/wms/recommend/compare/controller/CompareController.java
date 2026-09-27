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
 * 权限码 {@code compare:view} / {@code report:export} 由成员 a 的鉴权中间件统一校验。
 */
@RestController
@RequestMapping("/api/v1/plans")
public class CompareController {

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    @GetMapping
    public ApiResponse<List<Plan>> listPlans() {
        return ApiResponse.ok(compareService.listPlans());
    }

    @GetMapping("/{id}")
    public ApiResponse<Plan> getPlan(@PathVariable Long id) {
        return ApiResponse.ok(compareService.getPlan(id));
    }

    @PostMapping("/compare")
    public ApiResponse<CompareResult> compare(@Valid @RequestBody CompareRequest req) {
        return ApiResponse.ok(compareService.compare(req.planIds()));
    }

    @PostMapping("/{id}/suggestions")
    public ApiResponse<String> suggest(@PathVariable Long id) {
        return ApiResponse.ok(compareService.suggest(id));
    }

    @GetMapping("/{id}/export")
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
