package com.wms.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.domain.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计日志 Mapper。
 *
 * @author a
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
