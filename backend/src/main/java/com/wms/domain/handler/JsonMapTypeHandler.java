package com.wms.domain.handler;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;

/**
 * {@code plans.params} JSON 列 ↔ {@code Map<String, Object>} 的映射。
 *
 * <p>策略参数快照是自由结构（权重、分层规则、订单集 id 等，随策略不同而不同），
 * 因此不做强类型建模，只保证是合法 JSON 对象。
 *
 * @author a
 */
public class JsonMapTypeHandler extends JsonTypeHandler<Map<String, Object>> {

    /** 构造处理器。 */
    public JsonMapTypeHandler() {
        super(new TypeReference<>() {
        });
    }
}
