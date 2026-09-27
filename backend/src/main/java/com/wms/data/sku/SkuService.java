package com.wms.data.sku;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.common.PageResult;
import com.wms.data.sku.dto.CreateSkuRequest;
import com.wms.data.sku.dto.UpdateSkuRequest;
import com.wms.data.sku.vo.SkuVO;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Order;
import com.wms.domain.entity.Sku;
import com.wms.domain.mapper.LocationMapper;
import com.wms.domain.mapper.OrderMapper;
import com.wms.domain.mapper.SkuMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 货物（SKU）服务（A-B3），对应 API-029 ~ API-032（FR-1.2）。
 *
 * <p>{@code weight / turnoverRate / priority} 是推荐引擎（b）与仿真引擎（c）的核心输入，
 * 本服务保证其可写可读且口径一致（见 e2e 测试中的固定断言）。
 *
 * @author a
 */
@Service
public class SkuService {

    private static final Logger log = LoggerFactory.getLogger(SkuService.class);

    private final SkuMapper skuMapper;

    private final OrderMapper orderMapper;

    private final LocationMapper locationMapper;

    /**
     * 构造货物服务。
     *
     * @param skuMapper      SKU Mapper
     * @param orderMapper    订单 Mapper（删除前检查引用）
     * @param locationMapper 库位 Mapper（删除前检查占用）
     */
    public SkuService(SkuMapper skuMapper, OrderMapper orderMapper, LocationMapper locationMapper) {
        this.skuMapper = skuMapper;
        this.orderMapper = orderMapper;
        this.locationMapper = locationMapper;
    }

    /**
     * 货物分页列表（API-029，权限 sim:view）。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param category 品类过滤
     * @param keyword  SKU 编码/名称关键字
     * @return 分页结果
     */
    public PageResult<SkuVO> listSkus(Integer page, Integer pageSize, String category, String keyword) {
        int current = PageResult.normalizePage(page);
        int size = PageResult.normalizePageSize(pageSize);

        LambdaQueryWrapper<Sku> wrapper = Wrappers.<Sku>lambdaQuery()
                .eq(StringUtils.hasText(category), Sku::getCategory, category)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Sku::getCode, keyword)
                        .or()
                        .like(Sku::getName, keyword))
                .orderByAsc(Sku::getId);
        Page<Sku> result = skuMapper.selectPage(new Page<>(current, size), wrapper);

        List<SkuVO> items = result.getRecords().stream().map(SkuVO::from).toList();
        return new PageResult<>(items, result.getTotal(), current, size);
    }

    /**
     * 创建货物（API-030）。
     *
     * @param request 创建请求
     * @return 新建货物
     */
    @Transactional(rollbackFor = Exception.class)
    public SkuVO createSku(CreateSkuRequest request) {
        if (findByCode(request.skuCode()) != null) {
            throw new BizException(ErrorCode.SKU_CODE_DUPLICATE);
        }
        Sku entity = new Sku();
        entity.setCode(request.skuCode().trim());
        entity.setName(request.name().trim());
        entity.setWeight(request.weight());
        entity.setTurnoverRate(request.turnoverRate());
        entity.setPriority(request.priority());
        entity.setCategory(request.category());
        entity.setSize(request.size());
        entity.setRemark(request.remark());
        skuMapper.insert(entity);
        log.info("创建货物成功 id={} code={} 重量={} 频次={} 优先级={}", entity.getId(), entity.getCode(),
                entity.getWeight(), entity.getTurnoverRate(), entity.getPriority());
        return SkuVO.from(entity);
    }

    /**
     * 更新货物（API-031）。SKU 编码不可修改。
     *
     * @param skuId   货物 id
     * @param request 更新请求
     * @return 更新后的货物
     */
    @Transactional(rollbackFor = Exception.class)
    public SkuVO updateSku(Long skuId, UpdateSkuRequest request) {
        getByIdOrThrow(skuId);
        Sku update = new Sku();
        update.setId(skuId);
        update.setName(request.name());
        update.setWeight(request.weight());
        update.setTurnoverRate(request.turnoverRate());
        update.setPriority(request.priority());
        update.setCategory(request.category());
        update.setSize(request.size());
        update.setRemark(request.remark());
        skuMapper.updateById(update);
        log.info("更新货物成功 id={}", skuId);
        return SkuVO.from(getByIdOrThrow(skuId));
    }

    /**
     * 删除货物（API-032）。
     *
     * <p>保护规则：被库位占用或被订单引用时拒绝删除（42210），
     * 否则会产生「订单指向不存在的货物」这类脏数据。
     *
     * @param skuId 货物 id
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSku(Long skuId) {
        Sku sku = getByIdOrThrow(skuId);

        long occupied = locationMapper.selectCount(Wrappers.<Location>lambdaQuery()
                .eq(Location::getOccupiedSkuId, skuId));
        if (occupied > 0) {
            throw new BizException(ErrorCode.RESOURCE_IN_USE,
                    "货物 " + sku.getCode() + " 正被 " + occupied + " 个库位占用，无法删除");
        }
        long orderCount = orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getSkuId, skuId));
        if (orderCount > 0) {
            throw new BizException(ErrorCode.RESOURCE_IN_USE,
                    "货物 " + sku.getCode() + " 被 " + orderCount + " 条订单引用，无法删除");
        }
        skuMapper.deleteById(skuId);
        log.info("删除货物成功 id={} code={}", skuId, sku.getCode());
    }

    /**
     * 按编码查询货物。
     *
     * @param code SKU 编码
     * @return 货物实体，不存在时返回 null
     */
    public Sku findByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return skuMapper.selectOne(Wrappers.<Sku>lambdaQuery().eq(Sku::getCode, code.trim()));
    }

    /**
     * 按 id 查询货物，不存在时抛 40405。
     *
     * @param skuId 货物 id
     * @return 货物实体
     */
    public Sku getByIdOrThrow(Long skuId) {
        Sku sku = skuMapper.selectById(skuId);
        if (sku == null) {
            throw new BizException(ErrorCode.SKU_NOT_FOUND);
        }
        return sku;
    }

    /**
     * 批量查 SKU 编码（供订单列表一次组装，避免 N+1）。
     *
     * @param skuIds 货物 id 列表
     * @return skuId → 编码
     */
    public Map<Long, String> loadCodesByIds(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (Sku sku : skuMapper.selectBatchIds(skuIds.stream().distinct().toList())) {
            result.put(sku.getId(), sku.getCode());
        }
        return result;
    }
}
