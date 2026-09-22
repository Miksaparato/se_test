package com.wms.recommend.config.controller;

import com.wms.common.ApiResponse;
import com.wms.recommend.config.dto.CalibrateRequest;
import com.wms.recommend.config.dto.CalibrateResult;
import com.wms.recommend.config.service.ConfigService;
import com.wms.recommend.engine.ScoreWeightConfig;
import com.wms.recommend.engine.StorageRuleConfig;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权重 / 规则 / 校准配置接口（API-044 ~ API-048）。
 * 权限码 {@code config:manage} 由成员 a 的鉴权中间件统一校验。
 */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/weights")
    public ApiResponse<ScoreWeightConfig> getWeights() {
        return ApiResponse.ok(configService.getWeights());
    }

    @PutMapping("/weights")
    public ApiResponse<ScoreWeightConfig> updateWeights(@RequestBody ScoreWeightConfig req) {
        return ApiResponse.ok(configService.updateWeights(req));
    }

    @GetMapping("/rules")
    public ApiResponse<StorageRuleConfig> getRules() {
        return ApiResponse.ok(configService.getRules());
    }

    @PutMapping("/rules")
    public ApiResponse<StorageRuleConfig> updateRules(@RequestBody StorageRuleConfig req) {
        return ApiResponse.ok(configService.updateRules(req));
    }

    @PostMapping("/calibrate")
    public ApiResponse<CalibrateResult> calibrate(@Valid @RequestBody CalibrateRequest req) {
        var candidates = configService.calibrate(
                req.skuId(), req.warehouseId(), req.topN(), req.weights(), req.rules());
        return ApiResponse.ok(new CalibrateResult(
                req.weights() != null ? req.weights() : configService.getWeights(),
                req.rules() != null ? req.rules() : configService.getRules(),
                candidates));
    }
}
