package com.wms.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.domain.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限 Mapper。
 *
 * @author a
 */
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
