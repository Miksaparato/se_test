package com.wms.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.domain.entity.Location;
import org.apache.ibatis.annotations.Mapper;

/**
 * 库位 Mapper。
 *
 * @author a
 */
@Mapper
public interface LocationMapper extends BaseMapper<Location> {
}
