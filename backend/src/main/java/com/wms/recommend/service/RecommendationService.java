package com.wms.recommend.service;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Sku;
import com.wms.domain.entity.Warehouse;
import com.wms.domain.repository.WarehouseDataRepository;
import com.wms.recommend.config.service.ConfigService;
import com.wms.recommend.dto.AdoptResult;
import com.wms.recommend.dto.RecommendationDto;
import com.wms.recommend.dto.SkuBrief;
import com.wms.recommend.engine.LocationScore;
import com.wms.recommend.engine.ScoringEngine;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 库位智能推荐服务（B-B1 / B-F1）。
 * 对应 API-041（计算推荐）、API-042（查询）、API-043（采用推荐）。
 * 推荐结果为本模块自有状态，存放于内存（联调可按需落库）。
 */
@Service
public class RecommendationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final WarehouseDataRepository repository;
    private final ScoringEngine engine;
    private final ConfigService configService;

    private final Map<Long, RecommendationDto> recommendations = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    public RecommendationService(WarehouseDataRepository repository,
                                 ScoringEngine engine, ConfigService configService) {
        this.repository = repository;
        this.engine = engine;
        this.configService = configService;
    }

    public RecommendationDto recommend(Long skuId, Long warehouseId, Integer topN) {
        Sku sku = repository.findSku(skuId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "货物不存在: " + skuId));
        Warehouse wh = repository.findWarehouse(warehouseId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "仓库不存在: " + warehouseId));

        List<Location> free = repository.listFreeLocations(warehouseId);
        if (free.isEmpty()) {
            throw new BizException(ErrorCode.NO_FREE_LOCATION, "仓库「" + wh.getName() + "」暂无空闲库位");
        }

        List<LocationScore> scores = engine.score(sku, free, wh,
                configService.getWeights(), configService.getRules(), repository.occupiedSnapshot());
        if (scores.isEmpty()) {
            throw new BizException(ErrorCode.NO_ELIGIBLE_LOCATION, "无满足约束的库位（重货层高规则可能过滤了全部空位）");
        }

        int n = resolveTopN(topN, scores.size());
        List<LocationScore> top = new ArrayList<>(scores.subList(0, n));

        long id = seq.getAndIncrement();
        String recId = "REC-" + LocalDate.now().format(DATE_FMT) + "-" + String.format("%04d", id);
        RecommendationDto dto = new RecommendationDto(recId, SkuBrief.from(sku), top);
        recommendations.put(id, dto);
        return dto;
    }

    public RecommendationDto get(Long id) {
        RecommendationDto dto = recommendations.get(id);
        if (dto == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "推荐结果不存在: " + id);
        }
        return dto;
    }

    public AdoptResult adopt(Long id) {
        RecommendationDto dto = recommendations.get(id);
        if (dto == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "推荐结果不存在: " + id);
        }
        if (dto.candidates().isEmpty()) {
            throw new BizException(ErrorCode.EMPTY_RECOMMENDATION);
        }
        LocationScore top = dto.candidates().get(0);
        Location loc = repository.findLocation(top.getLocationId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "库位不存在: " + top.getLocationId()));
        if (!loc.isFree()) {
            throw new BizException(ErrorCode.LOCATION_OCCUPIED, "库位 " + loc.getCode() + " 已被占用");
        }
        repository.updateLocationStatus(loc.getId(), "occupied");
        return new AdoptResult(loc.getId(), loc.getCode(), "occupied", top.getScore());
    }

    private int resolveTopN(Integer topN, int size) {
        if (topN == null || topN <= 0) {
            return Math.min(10, size);
        }
        return Math.min(topN, size);
    }
}
