package com.wms.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充：统一维护 {@code created_at} / {@code updated_at}。
 *
 * <p>为什么需要：MyBatis-Plus 的 {@code insert} 会显式写出实体全部字段，未赋值时写的是
 * {@code NULL}，会覆盖数据库的 {@code DEFAULT CURRENT_TIMESTAMP(3)}。因此在应用侧统一填充，
 * 同时保证插入与更新落在同一进程时钟上（《代码规范》4.4：时间统一 UTC，
 * 连接串已设置 {@code serverTimezone=UTC}）。
 *
 * @author a
 */
@Component
public class WmsMetaObjectHandler implements MetaObjectHandler {

    /** 创建时间字段名。 */
    private static final String FIELD_CREATED_AT = "createdAt";

    /** 更新时间字段名。 */
    private static final String FIELD_UPDATED_AT = "updatedAt";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        fillIfNull(metaObject, FIELD_CREATED_AT, now);
        fillIfNull(metaObject, FIELD_UPDATED_AT, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        fillIfNull(metaObject, FIELD_UPDATED_AT, LocalDateTime.now());
    }

    /**
     * 仅在字段存在且为空时填充，避免覆盖业务显式赋值。
     *
     * @param metaObject 元对象
     * @param fieldName  字段名
     * @param value      填充值
     */
    private void fillIfNull(MetaObject metaObject, String fieldName, LocalDateTime value) {
        if (metaObject.hasSetter(fieldName) && getFieldValByName(fieldName, metaObject) == null) {
            setFieldValByName(fieldName, value, metaObject);
        }
    }
}
