package com.wms.data.location;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.common.PageResult;
import com.wms.data.location.dto.CreateLocationRequest;
import com.wms.data.location.dto.UpdateLocationRequest;
import com.wms.data.location.vo.LocationVO;
import com.wms.data.rack.RackService;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Rack;
import com.wms.domain.mapper.LocationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库位服务（A-B2）：库位 CRUD（API-025 ~ API-028）。
 *
 * <p>库位是推荐引擎（b）与仿真引擎（c）的核心数据源，本服务负责保证其**结构一致性**：
 * <ul>
 *   <li>编码全局唯一（FR-1.1 验收 1）；</li>
 *   <li>冗余列 {@code warehouse_id} 必须与所属货架一致，由本服务写入，调用方无需关心；</li>
 *   <li>{@code status = occupied} 与 {@code occupiedSkuId} 必须同时成立，避免出现
 *       「占用但没有货物」的脏数据；</li>
 *   <li>层号不得超出所属货架的 {@code layer_count}。</li>
 * </ul>
 *
 * @author a
 */
@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    /** 库位状态：空闲。 */
    public static final String STATUS_FREE = "free";

    /** 库位状态：占用。 */
    public static final String STATUS_OCCUPIED = "occupied";

    /** 库位状态：停用。 */
    public static final String STATUS_DISABLED = "disabled";

    /** 默认库位容量（体积口径，与数据库默认值一致）。 */
    public static final BigDecimal DEFAULT_CAPACITY = new BigDecimal("100.000");

    private final LocationMapper locationMapper;

    private final RackService rackService;

    /**
     * 构造库位服务。
     *
     * @param locationMapper 库位 Mapper
     * @param rackService    货架服务（校验货架存在与层数上限）
     */
    public LocationService(LocationMapper locationMapper, RackService rackService) {
        this.locationMapper = locationMapper;
        this.rackService = rackService;
    }

    /**
     * 库位分页列表（API-027，权限 sim:view）。
     *
     * @param page        页码
     * @param pageSize    每页条数
     * @param warehouseId 仓库过滤
     * @param rackId      货架过滤
     * @param status      状态过滤
     * @param layer       层号过滤
     * @return 分页结果
     */
    public PageResult<LocationVO> listLocations(Integer page, Integer pageSize, Long warehouseId,
                                                Long rackId, String status, Integer layer) {
        int current = PageResult.normalizePage(page);
        int size = PageResult.normalizePageSize(pageSize);

        LambdaQueryWrapper<Location> wrapper = Wrappers.<Location>lambdaQuery()
                .eq(warehouseId != null, Location::getWarehouseId, warehouseId)
                .eq(rackId != null, Location::getRackId, rackId)
                .eq(StringUtils.hasText(status), Location::getStatus, status)
                .eq(layer != null, Location::getLayer, layer)
                .orderByAsc(Location::getCode);
        Page<Location> result = locationMapper.selectPage(new Page<>(current, size), wrapper);

        List<LocationVO> items = result.getRecords().stream().map(LocationVO::from).toList();
        return new PageResult<>(items, result.getTotal(), current, size);
    }

    /**
     * 创建库位（API-025）。
     *
     * @param request 创建请求
     * @return 新建库位
     */
    @Transactional(rollbackFor = Exception.class)
    public LocationVO createLocation(CreateLocationRequest request) {
        Rack rack = rackService.getByIdOrThrow(request.rackId());
        LocationRules.validateLayer(request.layer(), rack);
        if (findByCode(request.code()) != null) {
            throw new BizException(ErrorCode.LOCATION_CODE_DUPLICATE);
        }
        String status = StringUtils.hasText(request.status()) ? request.status() : STATUS_FREE;
        LocationRules.validateStatusAndSku(status, request.occupiedSkuId());

        Location entity = new Location();
        entity.setRackId(rack.getId());
        entity.setWarehouseId(rack.getWarehouseId());
        entity.setCode(request.code().trim());
        entity.setX(request.x());
        entity.setY(request.y());
        entity.setLayer(request.layer());
        entity.setStatus(status);
        entity.setCapacity(request.capacity() == null ? DEFAULT_CAPACITY : request.capacity());
        entity.setOccupiedSkuId(STATUS_OCCUPIED.equals(status) ? request.occupiedSkuId() : null);
        locationMapper.insert(entity);
        log.info("创建库位成功 id={} code={} rack={} 层={}", entity.getId(), entity.getCode(),
                rack.getCode(), entity.getLayer());
        return LocationVO.from(entity);
    }

    /**
     * 更新库位（API-026）。编码与所属货架不可修改。
     *
     * @param locationId 库位 id
     * @param request    更新请求
     * @return 更新后的库位
     */
    @Transactional(rollbackFor = Exception.class)
    public LocationVO updateLocation(Long locationId, UpdateLocationRequest request) {
        Location current = getByIdOrThrow(locationId);
        Rack rack = rackService.getByIdOrThrow(current.getRackId());
        if (request.layer() != null) {
            LocationRules.validateLayer(request.layer(), rack);
        }

        // 规则集中在 LocationRules，便于单测与冻结口径评审
        String targetStatus = LocationRules.resolveTargetStatus(request.status(), current.getStatus());
        Long targetSkuId = LocationRules.resolveTargetSkuId(
                targetStatus, request.occupiedSkuId(), current.getOccupiedSkuId());
        LocationRules.validateStatusAndSku(targetStatus, targetSkuId);

        Location update = new Location();
        update.setId(locationId);
        update.setX(request.x());
        update.setY(request.y());
        update.setLayer(request.layer());
        update.setCapacity(request.capacity());
        update.setStatus(request.status());
        update.setOccupiedSkuId(targetSkuId);
        locationMapper.updateById(update);

        // 释放货物时必须显式置空：MyBatis-Plus 默认忽略 null 字段，无法靠 updateById 清空
        if (LocationRules.needsClearOccupiedSku(targetStatus, current.getOccupiedSkuId())) {
            locationMapper.update(null, Wrappers.<Location>lambdaUpdate()
                    .eq(Location::getId, locationId)
                    .set(Location::getOccupiedSkuId, null));
        }
        log.info("更新库位成功 id={} code={} 状态={} 占用货物={}", locationId, current.getCode(),
                targetStatus, targetSkuId);
        return LocationVO.from(getByIdOrThrow(locationId));
    }

    /**
     * 删除库位（API-028）。已占用或其他状态异常时拒绝删除。
     *
     * @param locationId 库位 id
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteLocation(Long locationId) {
        Location location = getByIdOrThrow(locationId);
        if (!LocationRules.deletable(location.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_IN_USE,
                    "库位 " + location.getCode() + " 处于占用状态，无法删除");
        }
        locationMapper.deleteById(locationId);
        log.info("删除库位成功 id={} code={}", locationId, location.getCode());
    }

    /**
     * 按编码查询库位。
     *
     * @param code 库位编码
     * @return 库位实体，不存在时返回 null
     */
    public Location findByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return locationMapper.selectOne(Wrappers.<Location>lambdaQuery().eq(Location::getCode, code.trim()));
    }

    /**
     * 按 id 查询库位，不存在时抛 40404。
     *
     * @param locationId 库位 id
     * @return 库位实体
     */
    public Location getByIdOrThrow(Long locationId) {
        Location location = locationMapper.selectById(locationId);
        if (location == null) {
            throw new BizException(ErrorCode.LOCATION_NOT_FOUND);
        }
        return location;
    }
}
