package com.wms.domain.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wms.domain.entity.type.LocationOccupancy;

import java.util.Map;

/**
 * {@code plans.location_map} JSON 列 ↔ {@code Map<库位id, LocationOccupancy>} 的映射。
 *
 * <p>显式保留 {@code String → LocationOccupancy} 的泛型：若交给只接收 {@code Class} 的
 * 通用 JSON 处理器，值会被反序列化成 {@code LinkedHashMap}，读取 {@code skuId} 时抛
 * ClassCastException。
 *
 * @author a
 */
public class LocationMapTypeHandler extends JsonTypeHandler<Map<String, LocationOccupancy>> {

    /** 构造处理器，保留「库位 id → 占用明细」的泛型信息。 */
    public LocationMapTypeHandler() {
        super(new TypeReference<>() {
        });
    }
}
