package com.wms.data.rack;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.data.location.LocationService;
import com.wms.data.rack.dto.CreateRackRequest;
import com.wms.data.rack.dto.UpdateRackRequest;
import com.wms.data.rack.vo.RackVO;
import com.wms.data.warehouse.WarehouseService;
import com.wms.domain.entity.Location;
import com.wms.domain.entity.Rack;
import com.wms.domain.mapper.LocationMapper;
import com.wms.domain.mapper.RackMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 货架服务（A-B2）：货架 CRUD 与库位批量生成（API-022 ~ API-024）。
 *
 * <p>库位批量生成规则（a 冻结，供 c 的平面图与 b 的推荐使用）：
 * <ul>
 *   <li>编码：{@code 巷道-货架序号-列-层}，如货架 A-01 的 3 列 2 层产出
 *       {@code A-01-01-01} ~ {@code A-01-03-02}；</li>
 *   <li>坐标：{@code orientation = row} 时 x 随列递增、y 不变；
 *       {@code orientation = column} 时 y 随列递增、x 不变；</li>
 *   <li>层号自 1 起（1 层最靠地面），同一列的各层共用同一平面坐标。</li>
 * </ul>
 *
 * @author a
 */
@Service
public class RackService {

    private static final Logger log = LoggerFactory.getLogger(RackService.class);

    /** 库位排布方向：沿 x 轴递增。 */
    public static final String ORIENTATION_ROW = "row";

    /** 库位排布方向：沿 y 轴递增。 */
    public static final String ORIENTATION_COLUMN = "column";

    private final RackMapper rackMapper;

    private final LocationMapper locationMapper;

    private final WarehouseService warehouseService;

    /**
     * 构造货架服务。
     *
     * <p>注意：这里刻意**不注入 {@code LocationService}**，否则会形成
     * {@code RackService ↔ LocationService} 循环依赖，Spring 构造器注入将直接启动失败。
     * 库位的批量写入由本服务直接用 {@link LocationMapper} 完成（同一事务内）。
     *
     * @param rackMapper       货架 Mapper
     * @param locationMapper   库位 Mapper
     * @param warehouseService 仓库服务（校验仓库存在）
     */
    public RackService(RackMapper rackMapper,
                       LocationMapper locationMapper,
                       WarehouseService warehouseService) {
        this.rackMapper = rackMapper;
        this.locationMapper = locationMapper;
        this.warehouseService = warehouseService;
    }

    /**
     * 创建货架（API-022），可选按列×层批量生成库位。
     *
     * @param request 创建请求
     * @return 新建货架（含库位数量）
     */
    @Transactional(rollbackFor = Exception.class)
    public RackVO createRack(CreateRackRequest request) {
        warehouseService.getByIdOrThrow(request.warehouseId());
        if (findByWarehouseAndCode(request.warehouseId(), request.code()) != null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该仓库下货架编码已存在：" + request.code());
        }

        Rack entity = new Rack();
        entity.setWarehouseId(request.warehouseId());
        entity.setCode(request.code().trim());
        entity.setAisle(request.aisle().trim());
        entity.setColumnCount(request.columnCount());
        entity.setLayerCount(request.layerCount());
        entity.setX(request.x() == null ? 0 : request.x());
        entity.setY(request.y() == null ? 0 : request.y());
        entity.setOrientation(StringUtils.hasText(request.orientation())
                ? request.orientation() : ORIENTATION_ROW);
        rackMapper.insert(entity);

        long locationCount = 0L;
        if (Boolean.TRUE.equals(request.generateLocations())) {
            locationCount = generateLocations(entity);
        }
        log.info("创建货架成功 id={} code={} 列={} 层={} 生成库位={}",
                entity.getId(), entity.getCode(), entity.getColumnCount(), entity.getLayerCount(),
                locationCount);
        return RackVO.from(entity, locationCount);
    }

    /**
     * 更新货架（API-023）。编码与巷道不可修改。
     *
     * @param rackId  货架 id
     * @param request 更新请求
     * @return 更新后的货架
     */
    @Transactional(rollbackFor = Exception.class)
    public RackVO updateRack(Long rackId, UpdateRackRequest request) {
        getByIdOrThrow(rackId);
        Rack update = new Rack();
        update.setId(rackId);
        update.setColumnCount(request.columnCount());
        update.setLayerCount(request.layerCount());
        update.setX(request.x());
        update.setY(request.y());
        update.setOrientation(request.orientation());
        rackMapper.updateById(update);
        log.info("更新货架成功 id={}", rackId);
        return getRack(rackId);
    }

    /**
     * 删除货架（API-024）。仍有库位时拒绝删除（42210）。
     *
     * @param rackId 货架 id
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteRack(Long rackId) {
        Rack rack = getByIdOrThrow(rackId);
        long locationCount = locationMapper.selectCount(Wrappers.<Location>lambdaQuery()
                .eq(Location::getRackId, rackId));
        if (locationCount > 0) {
            throw new BizException(ErrorCode.RESOURCE_IN_USE,
                    "该货架下仍有 " + locationCount + " 个库位，请先删除库位");
        }
        rackMapper.deleteById(rackId);
        log.info("删除货架成功 id={} code={}", rackId, rack.getCode());
    }

    /**
     * 查询货架详情。
     *
     * @param rackId 货架 id
     * @return 货架视图对象
     */
    public RackVO getRack(Long rackId) {
        Rack rack = getByIdOrThrow(rackId);
        long locationCount = locationMapper.selectCount(Wrappers.<Location>lambdaQuery()
                .eq(Location::getRackId, rackId));
        return RackVO.from(rack, locationCount);
    }

    /**
     * 按仓库与编码查询货架。
     *
     * @param warehouseId 仓库 id
     * @param code        货架编码
     * @return 货架实体，不存在时返回 null
     */
    public Rack findByWarehouseAndCode(Long warehouseId, String code) {
        if (warehouseId == null || !StringUtils.hasText(code)) {
            return null;
        }
        return rackMapper.selectOne(Wrappers.<Rack>lambdaQuery()
                .eq(Rack::getWarehouseId, warehouseId)
                .eq(Rack::getCode, code.trim()));
    }

    /**
     * 按 id 查询货架，不存在时抛 40403。
     *
     * @param rackId 货架 id
     * @return 货架实体
     */
    public Rack getByIdOrThrow(Long rackId) {
        Rack rack = rackMapper.selectById(rackId);
        if (rack == null) {
            throw new BizException(ErrorCode.RACK_NOT_FOUND);
        }
        return rack;
    }

    /**
     * 按货架批量生成库位：编码「巷道-货架序号-列-层」，逐条 insert（同一事务，NFR-1 下万级库位亦在秒级）。
     *
     * @param rack 货架实体
     * @return 生成数量
     */
    private long generateLocations(Rack rack) {
        String rackSeq = extractRackSequence(rack.getCode());
        List<Location> batch = new ArrayList<>();
        for (int column = 1; column <= rack.getColumnCount(); column++) {
            for (int layer = 1; layer <= rack.getLayerCount(); layer++) {
                String code = String.format("%s-%s-%02d-%02d", rack.getAisle(), rackSeq, column, layer);
                Location location = new Location();
                location.setRackId(rack.getId());
                location.setWarehouseId(rack.getWarehouseId());
                location.setCode(code);
                location.setX(resolveX(rack, column));
                location.setY(resolveY(rack, column));
                location.setLayer(layer);
                location.setStatus(LocationService.STATUS_FREE);
                location.setCapacity(LocationService.DEFAULT_CAPACITY);
                batch.add(location);
            }
        }
        // 批量查重：一次 in 查询判断编码是否已存在，避免循环单条查询（《代码规范》4.4）
        Set<String> codes = batch.stream().map(Location::getCode).collect(Collectors.toSet());
        List<Location> existing = locationMapper.selectList(Wrappers.<Location>lambdaQuery()
                .in(Location::getCode, codes));
        if (!existing.isEmpty()) {
            throw new BizException(ErrorCode.LOCATION_CODE_DUPLICATE,
                    "库位编码已存在：" + existing.get(0).getCode());
        }
        for (Location location : batch) {
            locationMapper.insert(location);
        }
        return batch.size();
    }

    /**
     * 计算货架列对应的平面坐标 x。
     *
     * @param rack   货架
     * @param column 列号，从 1 开始
     * @return 坐标 x
     */
    private int resolveX(Rack rack, int column) {
        int base = rack.getX() == null ? 0 : rack.getX();
        return ORIENTATION_COLUMN.equals(rack.getOrientation()) ? base : base + column - 1;
    }

    /**
     * 计算货架列对应的平面坐标 y。
     *
     * @param rack   货架
     * @param column 列号，从 1 开始
     * @return 坐标 y
     */
    private int resolveY(Rack rack, int column) {
        int base = rack.getY() == null ? 0 : rack.getY();
        return ORIENTATION_COLUMN.equals(rack.getOrientation()) ? base + column - 1 : base;
    }

    /**
     * 从货架编码中提取序号部分：{@code A-01} → {@code 01}；无连字符时原样返回。
     *
     * @param rackCode 货架编码
     * @return 序号字符串
     */
    private String extractRackSequence(String rackCode) {
        if (!StringUtils.hasText(rackCode)) {
            return "01";
        }
        String trimmed = rackCode.trim();
        int dash = trimmed.lastIndexOf('-');
        return dash >= 0 && dash < trimmed.length() - 1 ? trimmed.substring(dash + 1) : trimmed;
    }

    /**
     * 统计各货架的库位数。
     *
     * @param rackIds 货架 id 列表
     * @return rackId → 库位数
     */
    public Map<Long, Long> countLocationsByRack(List<Long> rackIds) {
        if (rackIds == null || rackIds.isEmpty()) {
            return Map.of();
        }
        return locationMapper.selectList(Wrappers.<Location>lambdaQuery()
                        .in(Location::getRackId, rackIds))
                .stream()
                .collect(Collectors.groupingBy(Location::getRackId, Collectors.counting()));
    }

    /**
     * 按仓库列出货架（供库位列表按货架过滤等场景）。
     *
     * @param warehouseId 仓库 id
     * @return 货架列表（按 id 升序）
     */
    public List<Rack> listByWarehouse(Long warehouseId) {
        return rackMapper.selectList(Wrappers.<Rack>lambdaQuery()
                        .eq(Rack::getWarehouseId, warehouseId))
                .stream()
                .sorted(Comparator.comparing(Rack::getId))
                .toList();
    }
}
