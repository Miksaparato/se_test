package com.wms.recommend.engine;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 权重配置校验单元测试（T-1）。 */
class ScoreWeightConfigTest {

    @Test
    void should_accept_default_weights() {
        assertDoesNotThrow(() -> new ScoreWeightConfig().validate());
        assertEquals(1.0, new ScoreWeightConfig().sum(), 1e-9);
    }

    @Test
    void should_reject_weights_not_summing_to_one() {
        ScoreWeightConfig w = new ScoreWeightConfig(0.3, 0.4, 0.2, 0.2); // 和 = 1.1
        BizException e = assertThrows(BizException.class, w::validate);
        assertEquals(ErrorCode.WEIGHT_SUM_INVALID.getCode(), e.getCode());
    }

    @Test
    void should_reject_negative_weight() {
        ScoreWeightConfig w = new ScoreWeightConfig(-0.1, 0.6, 0.3, 0.2);
        assertThrows(BizException.class, w::validate);
    }
}
