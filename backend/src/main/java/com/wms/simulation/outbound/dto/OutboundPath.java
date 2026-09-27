package com.wms.simulation.outbound.dto;

import com.wms.simulation.distance.Point;

import java.util.List;

/**
 * 拣选路径（API-061 响应元素），供前端平面图组件 {@code drawPath(points)} 绘制（C-F4）。
 *
 * <p>坐标序列为「库位 → 出库口」的折线；一张订单涉及多个库位时依次串接为
 * {@code [库位1, 出库口, 库位2, 出库口, ...]}，与搬运路程（各库位到出库口单程之和）口径一致。
 *
 * @param orderId 订单 id
 * @param orderNo 订单号
 * @param points  路径坐标序列
 * @author c
 */
public record OutboundPath(Long orderId, String orderNo, List<Point> points) {
}
