package com.wms.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.domain.entity.Plan;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分配方案 Mapper（c 写、b 读）。
 *
 * <p>复杂查询（按仓库过滤 + 取最新快照）如后续需要，统一写 XML（{@code resources/mapper/*.xml}），
 * 不在业务代码里拼 SQL（《代码规范》4.4）。
 *
 * @author c
 */
@Mapper
public interface PlanMapper extends BaseMapper<Plan> {
}
