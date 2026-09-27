package com.wms.data.location;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.domain.entity.Rack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 库位业务规则单元测试（a，T-3）。
 *
 * <p>纯 JUnit 5，无数据库、无 mock（《代码规范》4.7）。
 * 规则实现位于 {@link LocationRules}，被 {@link LocationService} 与前端提示共同引用，
 * 因此这里覆盖到的口径即生产口径。
 *
 * @author a
 */
class LocationRulesTest {

    /**
     * 构造货架。
     *
     * @param code       货架编码
     * @param layerCount 层数
     * @return 货架实体
     */
    private Rack rack(String code, Integer layerCount) {
        Rack rack = new Rack();
        rack.setId(9L);
        rack.setWarehouseId(1L);
        rack.setCode(code);
        rack.setLayerCount(layerCount);
        return rack;
    }

    @Test
    @DisplayName("层号在货架层数范围内应通过；等于层数上限也应通过")
    void should_accept_layer_within_rack_layer_count() {
        Rack rack = rack("A-01", 3);

        LocationRules.validateLayer(1, rack);
        LocationRules.validateLayer(3, rack);
    }

    @Test
    @DisplayName("层号超过货架层数应抛 40001，提示含层号与上限")
    void should_reject_layer_beyond_rack_layer_count() {
        Rack rack = rack("A-01", 2);

        assertThatThrownBy(() -> LocationRules.validateLayer(3, rack))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("层号 3")
                .hasMessageContaining("A-01")
                .hasMessageContaining("上限 2")
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("层号为空或货架未设层数时不做限制（表示不修改该字段）")
    void should_skip_layer_validation_when_absent() {
        LocationRules.validateLayer(null, rack("A-01", 2));
        LocationRules.validateLayer(5, rack("A-01", null));
        LocationRules.validateLayer(5, null);
    }

    @Test
    @DisplayName("occupied 状态必须带 occupiedSkuId，否则抛 40001")
    void should_require_sku_for_occupied_status() {
        assertThatThrownBy(() -> LocationRules.validateStatusAndSku("occupied", null))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("occupiedSkuId");
    }

    @Test
    @DisplayName("非 occupied 状态不得携带 occupiedSkuId，否则抛 40001")
    void should_reject_sku_for_non_occupied_status() {
        assertThatThrownBy(() -> LocationRules.validateStatusAndSku("free", 5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅 occupied 状态");

        assertThatThrownBy(() -> LocationRules.validateStatusAndSku("disabled", 5L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅 occupied 状态");
    }

    @Test
    @DisplayName("一致的状态与货物组合应通过")
    void should_accept_consistent_status_and_sku() {
        LocationRules.validateStatusAndSku("occupied", 5L);
        LocationRules.validateStatusAndSku("free", null);
        LocationRules.validateStatusAndSku("disabled", null);
    }

    @Test
    @DisplayName("目标状态：请求为空时沿用当前状态，否则以请求为准")
    void should_resolve_target_status() {
        assertThat(LocationRules.resolveTargetStatus(null, "free")).isEqualTo("free");
        assertThat(LocationRules.resolveTargetStatus("", "free")).isEqualTo("free");
        assertThat(LocationRules.resolveTargetStatus("   ", "free")).isEqualTo("free");
        assertThat(LocationRules.resolveTargetStatus("occupied", "free")).isEqualTo("occupied");
        assertThat(LocationRules.resolveTargetStatus("disabled", "occupied")).isEqualTo("disabled");
    }

    @Test
    @DisplayName("目标货物：改为非 occupied 视为释放货物（即便请求未带 skuId）")
    void should_release_sku_when_target_status_is_not_occupied() {
        assertThat(LocationRules.resolveTargetSkuId("free", null, 5L)).isNull();
        assertThat(LocationRules.resolveTargetSkuId("disabled", null, 5L)).isNull();
    }

    @Test
    @DisplayName("目标货物：保持 occupied 时优先取请求值，未提供则沿用当前值")
    void should_resolve_target_sku_for_occupied_status() {
        assertThat(LocationRules.resolveTargetSkuId("occupied", 7L, 5L)).isEqualTo(7L);
        assertThat(LocationRules.resolveTargetSkuId("occupied", null, 5L)).isEqualTo(5L);
        assertThat(LocationRules.resolveTargetSkuId("occupied", null, null)).isNull();
    }

    @Test
    @DisplayName("释放货物时必须显式清空占用列（MyBatis-Plus 忽略 null 的补偿逻辑）")
    void should_flag_clear_occupied_sku_when_releasing() {
        assertThat(LocationRules.needsClearOccupiedSku("free", 5L)).isTrue();
        assertThat(LocationRules.needsClearOccupiedSku("disabled", 5L)).isTrue();
        assertThat(LocationRules.needsClearOccupiedSku("free", null)).isFalse();
        assertThat(LocationRules.needsClearOccupiedSku("occupied", 5L)).isFalse();
    }

    @Test
    @DisplayName("占用状态的库位不可删除，空闲与停用可删除")
    void should_decide_deletable_by_status() {
        assertThat(LocationRules.deletable("occupied")).isFalse();
        assertThat(LocationRules.deletable("free")).isTrue();
        assertThat(LocationRules.deletable("disabled")).isTrue();
    }

    @Test
    @DisplayName("状态取值与《数据库设计说明书》冻结契约一致")
    void should_expose_frozen_status_constants() {
        assertThat(LocationService.STATUS_FREE).isEqualTo("free");
        assertThat(LocationService.STATUS_OCCUPIED).isEqualTo("occupied");
        assertThat(LocationService.STATUS_DISABLED).isEqualTo("disabled");
        assertThat(LocationService.DEFAULT_CAPACITY).isEqualByComparingTo(new BigDecimal("100.000"));
    }
}
