package com.wms.recommend.config.controller;

import com.wms.common.ApiResponse;
import com.wms.recommend.config.dto.CalibrateRequest;
import com.wms.recommend.config.dto.CalibrateResult;
import com.wms.recommend.config.service.ConfigService;
import com.wms.recommend.engine.ScoreWeightConfig;
import com.wms.recommend.engine.StorageRuleConfig;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权重 / 规则 / 校准配置接口（API-044 ~ API-048）。
 *
 * <p>《接口文档》第 10 节把本组接口的权限统一定为 {@code config:manage}（仅 admin），
 * 因此类级声明一次即可；对应 FR-2.3「权重与分层规则由管理员调整」。
 */
@RestController
@RequestMapping("/api/v1/config")
@PreAuthorize("hasAuthority('config:manage')")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * API-044 获取评分权重配置。
     *
     * @return 当前权重
     */
    @GetMapping("/weights")
    public ApiResponse<ScoreWeightConfig> getWeights() {
        return ApiResponse.ok(configService.getWeights());
    }

    /**
     * API-045 更新评分权重（四者之和须为 1，否则 42211）。
     *
     * @param req 新权重
     * @return 生效后的权重
     */
    @PutMapping("/weights")
    public ApiResponse<ScoreWeightConfig> updateWeights(@RequestBody ScoreWeightConfig req) {
        return ApiResponse.ok(configService.updateWeights(req));
    }

    /**
     * API-046 获取库位分层规则。
     *
     * @return 当前规则
     */
    @GetMapping("/rules")
    public ApiResponse<StorageRuleConfig> getRules() {
        return ApiResponse.ok(configService.getRules());
    }

    /**
     * API-047 更新库位分层规则（如「第 1 层放重货」）。
     *
     * @param req 新规则
     * @return 生效后的规则
     */
    @PutMapping("/rules")
    public ApiResponse<StorageRuleConfig> updateRules(@RequestBody StorageRuleConfig req) {
        return ApiResponse.ok(configService.updateRules(req));
    }

    /**
     * API-048 参数校准：按新参数重新评分（预览，不落库）。
     *
     * @param req 校准请求（skuId / warehouseId / topN / 可选权重与规则覆盖）
     * @return 生效参数与重算后的候选库位
     */
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
