package com.wms.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper（《代码规范》4.4：继承 {@code BaseMapper}，复杂 SQL 走 XML）。
 *
 * @author a
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
