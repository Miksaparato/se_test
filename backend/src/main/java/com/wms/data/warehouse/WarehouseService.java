package com.wms.data.warehouse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.common.PageResult;
import com.wms.data.location.vo.LocationVO;
import com.wms.data.warehouse.dto.CreateWarehouseRequest;
import com.wms.data.warehouse.dto.UpdateWarehouseRequest;
import com.wms.data.warehouse.vo.WarehouseLayoutVO;
import com.wms.data.warehouse.vo.WarehouseVO;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Rack;
import com.wms.domain.entity.Warehouse;
import com.wms.domain.mapper.LocationMapper;
import com.wms.domain.mapper.RackMapper;
import com.wms.domain.mapper.WarehouseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仓库服务（A-B1）：仓库 CRUD 与平面布局数据（API-016 ~ API-021）。
 *
 * <p>API-021 的返回结构是 c 的平面图公共组件（C-F1）与 b 的推荐高亮的输入契约，
 * 字段名与《接口文档》示例逐一对齐。
 *
 * @author a
 */
@Service
public class WarehouseService {

    private static final Logger log = LoggerFactory.getLogger(WarehouseService.class);

    private final WarehouseMapper warehouseMapper;

    private final RackMapper rackMapper;

    private final LocationMapper locationMapper;

    /**
     * 构造仓库服务。
     *
     * @param warehouseMapper 仓库 Mapper
     * @param rackMapper      货架 Mapper
     * @param locationMapper  库位 Mapper
     */
    public WarehouseService(WarehouseMapper warehouseMapper,
                            RackMapper rackMapper,
                            LocationMapper locationMapper) {
        this.warehouseMapper = warehouseMapper;
        this.rackMapper = rackMapper;
        this.locationMapper = locationMapper;
    }

    /**
     * 仓库分页列表（API-017，权限 sim:view）。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param keyword  名称/编码关键字
     * @return 分页结果
     */
    public PageResult<WarehouseVO> listWarehouses(Integer page, Integer pageSize, String keyword) {
        int current = PageResult.normalizePage(page);
        int size = PageResult.normalizePageSize(pageSize);

        LambdaQueryWrapper<Warehouse> wrapper = Wrappers.<Warehouse>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Warehouse::getCode, keyword)
                        .or()
                        .like(Warehouse::getName, keyword))
                .orderByAsc(Warehouse::getId);
        Page<Warehouse> result = warehouseMapper.selectPage(new Page<>(current, size), wrapper);

        Map<Long, Long> rackCounts = countRacksByWarehouse(result.getRecords().stream()
                .map(Warehouse::getId).toList());
        List<WarehouseVO> items = result.getRecords().stream()
                .map(warehouse -> WarehouseVO.from(warehouse,
                        rackCounts.getOrDefault(warehouse.getId(), 0L)))
                .toList();
        return new PageResult<>(items, result.getTotal(), current, size);
    }

    /**
     * 仓库详情（API-018，权限 sim:view）。
     *
     * @param warehouseId 仓库 id
     * @return 仓库视图对象
     */
    public WarehouseVO getWarehouse(Long warehouseId) {
        Warehouse warehouse = getByIdOrThrow(warehouseId);
        return WarehouseVO.from(warehouse,
                countRacksByWarehouse(List.of(warehouseId)).getOrDefault(warehouseId, 0L));
    }

    /**
     * 创建仓库（API-016）。
     *
     * @param request 创建请求
     * @return 新建仓库
     */
    @Transactional(rollbackFor = Exception.class)
    public WarehouseVO createWarehouse(CreateWarehouseRequest request) {
        if (findByCode(request.code()) != null) {
            throw new BizException(ErrorCode.WAREHOUSE_CODE_DUPLICATE);
        }
        Warehouse entity = new Warehouse();
        entity.setCode(request.code().trim());
        entity.setName(request.name().trim());
        entity.setLength(request.length());
        entity.setWidth(request.width());
        entity.setHeight(request.height());
        entity.setExitX(request.exitX() == null ? 0 : request.exitX());
        entity.setExitY(request.exitY() == null ? 0 : request.exitY());
        entity.setExitLayer(request.exitLayer() == null ? 1 : request.exitLayer());
        entity.setRemark(request.remark());
        warehouseMapper.insert(entity);
        log.info("创建仓库成功 id={} code={} 出库口=({}, {})",
                entity.getId(), entity.getCode(), entity.getExitX(), entity.getExitY());
        return WarehouseVO.from(entity, 0L);
    }

    /**
     * 更新仓库（API-019）。编码不可修改。
     *
     * @param warehouseId 仓库 id
     * @param request     更新请求
     * @return 更新后的仓库
     */
    @Transactional(rollbackFor = Exception.class)
    public WarehouseVO updateWarehouse(Long warehouseId, UpdateWarehouseRequest request) {
        getByIdOrThrow(warehouseId);
        Warehouse update = new Warehouse();
        update.setId(warehouseId);
        update.setName(request.name());
        update.setLength(request.length());
        update.setWidth(request.width());
        update.setHeight(request.height());
        update.setExitX(request.exitX());
        update.setExitY(request.exitY());
        update.setExitLayer(request.exitLayer());
        update.setRemark(request.remark());
        warehouseMapper.updateById(update);
        log.info("更新仓库成功 id={}", warehouseId);
        return getWarehouse(warehouseId);
    }

    /**
     * 删除仓库（API-020）。仍存在货架时拒绝删除（42210）。
     *
     * @param warehouseId 仓库 id
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteWarehouse(Long warehouseId) {
        Warehouse warehouse = getByIdOrThrow(warehouseId);
        long rackCount = countRacksByWarehouse(List.of(warehouseId)).getOrDefault(warehouseId, 0L);
        if (rackCount > 0) {
            throw new BizException(ErrorCode.RESOURCE_IN_USE,
                    "该仓库下仍有 " + rackCount + " 个货架，请先删除货架");
        }
        warehouseMapper.deleteById(warehouseId);
        log.info("删除仓库成功 id={} code={}", warehouseId, warehouse.getCode());
    }

    /**
     * 仓库平面布局（API-021）：一次取齐货架与库位，内存组装，避免 N+1（NFR-1）。
     *
     * @param warehouseId 仓库 id
     * @return 布局数据（货架下嵌套库位）
     */
    public WarehouseLayoutVO getLayout(Long warehouseId) {
        Warehouse warehouse = getByIdOrThrow(warehouseId);

        List<Rack> racks = rackMapper.selectList(Wrappers.<Rack>lambdaQuery()
                .eq(Rack::getWarehouseId, warehouseId)
                .orderByAsc(Rack::getId));

        Map<Long, List<Location>> locationsByRack = racks.isEmpty()
                ? Map.of()
                : locationMapper.selectList(Wrappers.<Location>lambdaQuery()
                        .eq(Location::getWarehouseId, warehouseId)
                        .orderByAsc(Location::getRackId)
                        .orderByAsc(Location::getLayer)
                        .orderByAsc(Location::getX)
                        .orderByAsc(Location::getY))
                .stream()
                .collect(Collectors.groupingBy(Location::getRackId));

        List<WarehouseLayoutVO.LayoutRack> layoutRacks = new ArrayList<>();
        for (Rack rack : racks) {
            List<LocationVO.LayoutItem> items = locationsByRack
                    .getOrDefault(rack.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(Location::getCode))
                    .map(LocationVO::toLayoutItem)
                    .toList();
            layoutRacks.add(new WarehouseLayoutVO.LayoutRack(
                    rack.getId(),
                    rack.getCode(),
                    rack.getAisle(),
                    rack.getColumnCount(),
                    rack.getLayerCount(),
                    rack.getX(),
                    rack.getY(),
                    rack.getOrientation(),
                    items));
        }

        return WarehouseLayoutVO.of(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getLength(),
                warehouse.getWidth(),
                warehouse.getHeight(),
                new WarehouseVO.ExitPoint(warehouse.getExitX(), warehouse.getExitY(),
                        warehouse.getExitLayer()),
                layoutRacks);
    }

    /**
     * 按编码查询仓库。
     *
     * @param code 仓库编码
     * @return 仓库实体，不存在时返回 null
     */
    public Warehouse findByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return warehouseMapper.selectOne(Wrappers.<Warehouse>lambdaQuery().eq(Warehouse::getCode, code));
    }

    /**
     * 按 id 查询仓库，不存在时抛 40402。
     *
     * @param warehouseId 仓库 id
     * @return 仓库实体
     */
    public Warehouse getByIdOrThrow(Long warehouseId) {
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw new BizException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        return warehouse;
    }

    /**
     * 统计各仓库的货架数（一次查询，避免 N+1）。
     *
     * @param warehouseIds 仓库 id 列表
     * @return warehouseId → 货架数
     */
    private Map<Long, Long> countRacksByWarehouse(List<Long> warehouseIds) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return Map.of();
        }
        return rackMapper.selectList(Wrappers.<Rack>lambdaQuery()
                        .in(Rack::getWarehouseId, warehouseIds))
                .stream()
                .collect(Collectors.groupingBy(Rack::getWarehouseId, Collectors.counting()));
    }
}
