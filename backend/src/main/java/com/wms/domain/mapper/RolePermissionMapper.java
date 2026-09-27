package com.wms.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.domain.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色-权限关联 Mapper。
 *
 * @author a
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
