package com.wms.recommend.config.service;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import com.wms.domain.repository.WarehouseDataRepository;
import com.wms.recommend.engine.LocationScore;
import com.wms.recommend.engine.ScoreWeightConfig;
import com.wms.recommend.engine.ScoringEngine;
import com.wms.recommend.engine.StorageRuleConfig;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权重 / 规则 / 校准配置服务（B-B3 / B-B4 / B-B9）。
 * 对应 API-044 ~ API-048。配置为本模块自有状态（管理员可调，联调可按需落库）。
 */
@Service
public class ConfigService {

    private final WarehouseDataRepository repository;
    private final ScoringEngine engine;

    private final ScoreWeightConfig weights = new ScoreWeightConfig();
    private final StorageRuleConfig rules = new StorageRuleConfig();

    public ConfigService(WarehouseDataRepository repository, ScoringEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    public ScoreWeightConfig getWeights() {
        return copyWeights(weights);
    }

    public ScoreWeightConfig updateWeights(ScoreWeightConfig req) {
        if (req == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        req.validate();
        weights.setWeight(req.getWeight());
        weights.setFreq(req.getFreq());
        weights.setPriority(req.getPriority());
        weights.setOther(req.getOther());
        return copyWeights(weights);
    }

    public StorageRuleConfig getRules() {
        return copyRules(rules);
    }

    public StorageRuleConfig updateRules(StorageRuleConfig req) {
        if (req == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        req.validate();
        rules.setHeavyWeightThreshold(req.getHeavyWeightThreshold());
        rules.setMaxLayerForHeavy(req.getMaxLayerForHeavy());
        rules.setMaxWeightNorm(req.getMaxWeightNorm());
        rules.setMaxTurnover(req.getMaxTurnover());
        rules.setMaxPriority(req.getMaxPriority());
        rules.setGoldenZoneRadius(req.getGoldenZoneRadius());
        rules.setCategoryMatchEnabled(req.isCategoryMatchEnabled());
        return copyRules(rules);
    }

    /** 参数校准：按新权重/规则重新评分（不落库，仅预览）。 */
    public List<LocationScore> calibrate(Long skuId, Long warehouseId, Integer topN,
                                         ScoreWeightConfig weightsOverride, StorageRuleConfig rulesOverride) {
        Sku sku = repository.findSku(skuId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "货物不存在: " + skuId));
        Warehouse wh = repository.findWarehouse(warehouseId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "仓库不存在: " + warehouseId));

        ScoreWeightConfig w = weightsOverride != null ? weightsOverride : copyWeights(weights);
        StorageRuleConfig r = rulesOverride != null ? rulesOverride : copyRules(rules);
        w.validate();
        r.validate();

        List<Location> free = repository.listFreeLocations(warehouseId);
        List<LocationScore> scores = engine.score(sku, free, wh, w, r, repository.occupiedSnapshot());
        int n = topN == null || topN <= 0 ? Math.min(10, scores.size()) : Math.min(topN, scores.size());
        return scores.subList(0, Math.max(0, n));
    }

    private ScoreWeightConfig copyWeights(ScoreWeightConfig src) {
        return new ScoreWeightConfig(src.getWeight(), src.getFreq(), src.getPriority(), src.getOther());
    }

    private StorageRuleConfig copyRules(StorageRuleConfig src) {
        StorageRuleConfig c = new StorageRuleConfig();
        c.setHeavyWeightThreshold(src.getHeavyWeightThreshold());
        c.setMaxLayerForHeavy(src.getMaxLayerForHeavy());
        c.setMaxWeightNorm(src.getMaxWeightNorm());
        c.setMaxTurnover(src.getMaxTurnover());
        c.setMaxPriority(src.getMaxPriority());
        c.setGoldenZoneRadius(src.getGoldenZoneRadius());
        c.setCategoryMatchEnabled(src.isCategoryMatchEnabled());
        return c;
    }
}
