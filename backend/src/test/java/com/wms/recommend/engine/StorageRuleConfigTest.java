package com.wms.recommend.engine;

import com.wms.common.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 分层规则单元测试（T-1，算法 5.4 硬约束）。 */
class StorageRuleConfigTest {

    @Test
    void should_allow_heavy_on_low_layer() {
        StorageRuleConfig r = new StorageRuleConfig();
        assertTrue(r.allowsHeavyOnLayer(150, 1), "重货应允许放第 1 层");
        assertTrue(r.allowsHeavyOnLayer(150, 2), "重货应允许放第 2 层（<= maxLayerForHeavy）");
    }

    @Test
    void should_reject_heavy_on_high_layer() {
        StorageRuleConfig r = new StorageRuleConfig();
        assertFalse(r.allowsHeavyOnLayer(150, 3), "重货禁止放到超过预设层高");
    }

    @Test
    void should_not_restrict_light_sku() {
        StorageRuleConfig r = new StorageRuleConfig();
        assertTrue(r.allowsHeavyOnLayer(80, 3), "轻货不受层高限制");
    }

    @Test
    void should_reject_invalid_threshold() {
        StorageRuleConfig r = new StorageRuleConfig();
        r.setHeavyWeightThreshold(0);
        assertThrows(BizException.class, r::validate);
    }
}
