package com.wms.recommend.controller;

import com.wms.common.ApiResponse;
import com.wms.recommend.dto.AdoptResult;
import com.wms.recommend.dto.RecommendRequest;
import com.wms.recommend.dto.RecommendationDto;
import com.wms.recommend.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库位智能推荐接口（API-041 ~ API-043）。
 * 权限码 {@code recommend:view} 由成员 a 的鉴权中间件统一校验（A-B8）。
 */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public ApiResponse<RecommendationDto> recommend(@Valid @RequestBody RecommendRequest req) {
        return ApiResponse.ok(recommendationService.recommend(req.skuId(), req.warehouseId(), req.topN()));
    }

    @GetMapping("/{id}")
    public ApiResponse<RecommendationDto> get(@PathVariable Long id) {
        return ApiResponse.ok(recommendationService.get(id));
    }

    @PostMapping("/{id}/adopt")
    public ApiResponse<AdoptResult> adopt(@PathVariable Long id) {
        return ApiResponse.ok(recommendationService.adopt(id));
    }
}
