package com.wms.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.domain.entity.UserRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-角色关联 Mapper。
 *
 * @author a
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {
}
