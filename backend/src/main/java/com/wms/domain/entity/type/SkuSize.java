package com.wms.domain.entity.type;

import jakarta.validation.constraints.Min;

/**
 * 货物尺寸（SKU 的 {@code size} JSON 列），与《接口文档》API-030 请求示例一致：
 *
 * <pre>
 * "size": { "length": 30, "width": 20, "height": 10 }
 * </pre>
 *
 * <p>体积 = length × width × height，用于与 {@code locations.capacity} 做容量校验
 * （《需求文档》5.4 约束三，由 b 的推荐引擎与 c 的约束校验模块执行）。
 *
 * @param length 长
 * @param width  宽
 * @param height 高
 * @author a
 */
public record SkuSize(
        @Min(value = 0, message = "尺寸长不能为负数")
        Integer length,

        @Min(value = 0, message = "尺寸宽不能为负数")
        Integer width,

        @Min(value = 0, message = "尺寸高不能为负数")
        Integer height) {

    /**
     * 计算体积。
     *
     * @return 体积（长×宽×高），任一维为空时返回 0
     */
    public int volume() {
        if (length == null || width == null || height == null) {
            return 0;
        }
        return length * width * height;
    }

    /**
     * 判断该尺寸能否放进指定容量的库位（《需求文档》4.4 约束三：尺寸不得超出库位容量）。
     *
     * <p><b>口径</b>：{@code locations.capacity} 与 {@code skus.size} 同为**体积口径**，
     * 单位由使用方统一（本工程演示数据用立方厘米）。未录入尺寸（任一维为空）或库位未设容量时
     * 视为无该约束，返回 true——与《数据库设计说明书》5.2「若录入尺寸，则校验」一致。
     *
     * <p>本方法由 b 的推荐引擎与 c 的约束校验共用，避免两处各写一份导致
     * 「推荐出来的库位被仿真判为不可用」。
     *
     * @param capacity 库位容量，可为 null
     * @return 放得下返回 true
     */
    public boolean fitsWithin(java.math.BigDecimal capacity) {
        int volume = volume();
        if (volume <= 0 || capacity == null) {
            return true;
        }
        return capacity.doubleValue() >= volume;
    }
}
