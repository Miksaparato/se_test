package com.wms.data.order;

import com.wms.common.ApiResponse;
import com.wms.common.PageResult;
import com.wms.data.order.dto.CreateOrderRequest;
import com.wms.data.order.dto.UpdateOrderRequest;
import com.wms.data.order.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单控制器，对应《接口文档》API-033 ~ API-036。
 *
 * <p>读需 {@code sim:view}，写需 {@code sku:manage}。
 * 注意：API-062（随机测试订单集）由 c 实现，也在 {@code /orders} 路径下，
 * 故本控制器不声明 {@code /generate} 映射。
 *
 * @author a
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    /**
     * 构造订单控制器。
     *
     * @param orderService 订单服务
     */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * API-033 订单列表（分页、可按状态过滤），默认按优先级降序 + 下达时间升序。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param status   状态过滤
     * @param skuId    货物过滤
     * @param orderNo  订单号过滤
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("hasAuthority('sim:view')")
    public ApiResponse<PageResult<OrderVO>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(name = "sku_id", required = false) Long skuId,
            @RequestParam(name = "order_no", required = false) String orderNo) {
        return ApiResponse.ok(orderService.listOrders(page, pageSize, status, skuId, orderNo));
    }

    /**
     * API-034 创建出库订单。
     *
     * @param request 创建请求
     * @return 新建订单
     */
    @PostMapping
    @PreAuthorize("hasAuthority('sku:manage')")
    public ApiResponse<OrderVO> create(@RequestBody @Valid CreateOrderRequest request) {
        return ApiResponse.ok(orderService.createOrder(request));
    }

    /**
     * API-035 更新订单。
     *
     * @param id      订单 id
     * @param request 更新请求
     * @return 更新后的订单
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sku:manage')")
    public ApiResponse<OrderVO> update(@PathVariable Long id,
                                       @RequestBody @Valid UpdateOrderRequest request) {
        return ApiResponse.ok(orderService.updateOrder(id, request));
    }

    /**
     * API-036 删除订单（仅 pending / cancelled 可删）。
     *
     * @param id 订单 id
     * @return 无数据的成功响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sku:manage')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ApiResponse.ok();
    }
}
