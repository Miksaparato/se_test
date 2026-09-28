package com.wms.recommend.controller;

import com.wms.common.ApiResponse;
import com.wms.recommend.dto.AdoptResult;
import com.wms.recommend.dto.RecommendRequest;
import com.wms.recommend.dto.RecommendationDto;
import com.wms.recommend.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库位智能推荐接口（API-041 ~ API-043）。
 *
 * <p>权限码 {@code recommend:view} 由 a 的鉴权中间件（A-B8）通过 {@code @PreAuthorize}
 * 强制校验，与《接口文档》第 9 节的权限列一致；前端显隐只是体验优化。
 */
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /**
     * API-041 对空闲库位计算评分并排序。
     *
     * @param req 推荐请求（skuId / warehouseId / topN）
     * @return 推荐结果
     */
    @PostMapping
    @PreAuthorize("hasAuthority('recommend:view')")
    public ApiResponse<RecommendationDto> recommend(@Valid @RequestBody RecommendRequest req) {
        return ApiResponse.ok(recommendationService.recommend(req.skuId(), req.warehouseId(), req.topN()));
    }

    /**
     * API-042 查询推荐结果。
     *
     * @param id 推荐结果 id，如 REC-20260910-0001
     * @return 推荐结果
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('recommend:view')")
    public ApiResponse<RecommendationDto> get(@PathVariable String id) {
        return ApiResponse.ok(recommendationService.get(id));
    }

    /**
     * API-043 一键采用推荐，完成入库并更新库位占用状态。
     *
     * @param id 推荐结果 id
     * @return 采用的库位与得分
     */
    @PostMapping("/{id}/adopt")
    @PreAuthorize("hasAuthority('recommend:view')")
    public ApiResponse<AdoptResult> adopt(@PathVariable String id) {
        return ApiResponse.ok(recommendationService.adopt(id));
    }
}
