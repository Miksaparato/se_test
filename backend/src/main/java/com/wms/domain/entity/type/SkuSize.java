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
}
