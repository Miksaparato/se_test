package com.wms.domain.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wms.domain.entity.type.PlanResult;

/**
 * {@code plans.result} JSON 列 ↔ {@link PlanResult} 的映射。
 *
 * @author a
 */
public class PlanResultTypeHandler extends JsonTypeHandler<PlanResult> {

    /** 构造处理器，保留 {@link PlanResult} 的泛型/记录结构信息。 */
    public PlanResultTypeHandler() {
        super(new TypeReference<>() {
        });
    }
}
