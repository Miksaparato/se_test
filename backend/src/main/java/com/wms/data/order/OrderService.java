package com.wms.data.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.common.PageResult;
import com.wms.data.order.dto.CreateOrderRequest;
import com.wms.data.order.dto.UpdateOrderRequest;
import com.wms.data.order.vo.OrderVO;
import com.wms.data.sku.SkuService;
import com.wms.domain.entity.Order;
import com.wms.domain.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 出库订单服务（A-B5），对应 API-033 ~ API-036（FR-1.3）。
 *
 * <p>订单状态机：{@code pending} 待出库 → {@code picking} 拣选中 → {@code completed} 已完成，
 * 另有 {@code cancelled} 已取消。删除仅允许 {@code pending} / {@code cancelled} 状态，
 * 避免删掉正在出库或已完成的记录（出库仿真的过程数据）。
 *
 * <p>出库仿真排序（给 c 的 C-B4）：优先级降序 + 下达时间升序，排序所需索引见 V1 脚本。
 *
 * @author a
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** 订单状态：待出库。 */
    public static final String STATUS_PENDING = "pending";

    /** 订单状态：拣选中。 */
    public static final String STATUS_PICKING = "picking";

    /** 订单状态：已完成。 */
    public static final String STATUS_COMPLETED = "completed";

    /** 订单状态：已取消。 */
    public static final String STATUS_CANCELLED = "cancelled";

    /** 订单号日期格式。 */
    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderMapper orderMapper;

    private final SkuService skuService;

    /**
     * 构造订单服务。
     *
     * @param orderMapper 订单 Mapper
     * @param skuService  货物服务（校验 skuId 存在）
     */
    public OrderService(OrderMapper orderMapper, SkuService skuService) {
        this.orderMapper = orderMapper;
        this.skuService = skuService;
    }

    /**
     * 订单分页列表（API-033，权限 sim:view）。
     *
     * <p>默认按「优先级降序 + 下达时间升序」排序，与出库仿真的拣选顺序一致（FR-4.1）。
     *
     * @param page     页码
     * @param pageSize 每页条数
     * @param status   状态过滤
     * @param skuId    货物过滤
     * @param orderNo  订单号过滤
     * @return 分页结果
     */
    public PageResult<OrderVO> listOrders(Integer page, Integer pageSize, String status, Long skuId,
                                          String orderNo) {
        int current = PageResult.normalizePage(page);
        int size = PageResult.normalizePageSize(pageSize);

        LambdaQueryWrapper<Order> wrapper = Wrappers.<Order>lambdaQuery()
                .eq(StringUtils.hasText(status), Order::getStatus, status)
                .eq(skuId != null, Order::getSkuId, skuId)
                .like(StringUtils.hasText(orderNo), Order::getOrderNo, orderNo)
                .orderByDesc(Order::getPriority)
                .orderByAsc(Order::getPlacedAt)
                .orderByAsc(Order::getId);
        Page<Order> result = orderMapper.selectPage(new Page<>(current, size), wrapper);

        Map<Long, String> skuCodes = skuService.loadCodesByIds(result.getRecords().stream()
                .map(Order::getSkuId).toList());
        List<OrderVO> items = result.getRecords().stream()
                .map(order -> OrderVO.from(order, skuCodes.get(order.getSkuId())))
                .toList();
        return new PageResult<>(items, result.getTotal(), current, size);
    }

    /**
     * 创建订单（API-034）。
     *
     * @param request 创建请求
     * @return 新建订单
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(CreateOrderRequest request) {
        skuService.getByIdOrThrow(request.skuId());

        String orderNo = StringUtils.hasText(request.orderNo())
                ? request.orderNo().trim() : nextOrderNo();
        if (findByOrderNo(orderNo) != null) {
            throw new BizException(ErrorCode.ORDER_NO_DUPLICATE);
        }

        Order entity = new Order();
        entity.setOrderNo(orderNo);
        entity.setSkuId(request.skuId());
        entity.setQuantity(request.quantity());
        entity.setPriority(request.priority());
        entity.setPlacedAt(request.placedAt() == null ? LocalDateTime.now() : request.placedAt());
        entity.setStatus(StringUtils.hasText(request.status()) ? request.status() : STATUS_PENDING);
        entity.setRemark(request.remark());
        orderMapper.insert(entity);
        log.info("创建订单成功 id={} orderNo={} skuId={} 数量={} 优先级={}", entity.getId(),
                entity.getOrderNo(), entity.getSkuId(), entity.getQuantity(), entity.getPriority());
        return toVO(entity);
    }

    /**
     * 更新订单（API-035）。订单号不可修改。
     *
     * @param orderId 订单 id
     * @param request 更新请求
     * @return 更新后的订单
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO updateOrder(Long orderId, UpdateOrderRequest request) {
        Order current = getByIdOrThrow(orderId);
        if (STATUS_COMPLETED.equals(current.getStatus())
                && (request.quantity() != null || request.skuId() != null)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "已完成的订单不允许修改货物或数量");
        }
        if (request.skuId() != null) {
            skuService.getByIdOrThrow(request.skuId());
        }

        Order update = new Order();
        update.setId(orderId);
        update.setSkuId(request.skuId());
        update.setQuantity(request.quantity());
        update.setPriority(request.priority());
        update.setPlacedAt(request.placedAt());
        update.setStatus(request.status());
        update.setRemark(request.remark());
        orderMapper.updateById(update);
        log.info("更新订单成功 id={} orderNo={}", orderId, current.getOrderNo());
        return toVO(getByIdOrThrow(orderId));
    }

    /**
     * 删除订单（API-036）。仅允许删除 pending / cancelled 状态。
     *
     * @param orderId 订单 id
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long orderId) {
        Order order = getByIdOrThrow(orderId);
        if (!STATUS_PENDING.equals(order.getStatus()) && !STATUS_CANCELLED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.RESOURCE_IN_USE,
                    "订单 " + order.getOrderNo() + " 当前状态为 " + order.getStatus() + "，不允许删除");
        }
        orderMapper.deleteById(orderId);
        log.info("删除订单成功 id={} orderNo={}", orderId, order.getOrderNo());
    }

    /**
     * 按订单号查询。
     *
     * @param orderNo 订单号
     * @return 订单实体，不存在时返回 null
     */
    public Order findByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        return orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo.trim()));
    }

    // ------------------------------------------------------------------
    // 以下三个方法由 c 的仿真模块消费（《代码规范》10.3：跨模块只通过公开 Service 调用）
    // ------------------------------------------------------------------

    /**
     * 查询参与出库仿真的待出库订单，按「优先级降序 + 下达时间升序」排序（FR-4.1）。
     *
     * <p>排序主路径命中索引 {@code idx_orders_status_priority_placed}（《数据库设计说明书》5.3）。
     *
     * @return 待出库订单列表
     */
    public List<Order> listPendingForSimulation() {
        return orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getStatus, STATUS_PENDING)
                .orderByDesc(Order::getPriority)
                .orderByAsc(Order::getPlacedAt)
                .orderByAsc(Order::getId));
    }

    /**
     * 按 id 批量查询订单（出库仿真指定 orderIds 时使用）。
     *
     * <p>用 {@code selectBatchIds} 一次取齐，避免循环单条查询（《代码规范》4.4）。
     *
     * @param orderIds 订单 id 列表
     * @return 订单列表；入参为空时返回空列表
     */
    public List<Order> listByIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        return orderMapper.selectBatchIds(orderIds.stream().filter(java.util.Objects::nonNull).distinct().toList());
    }

    /**
     * 批量创建订单（供 c 的 API-062「随机测试订单集生成」使用，C-B8）。
     *
     * <p>与单条创建相比做了两处优化：
     * <ul>
     *   <li>skuId 存在性用一次批量查询校验，不在循环里查库；</li>
     *   <li>用 {@code insertBatch} 一条 SQL 写入全部订单（SQL 见 {@code mapper/OrderMapper.xml}）。</li>
     * </ul>
     * 未显式给订单号的条目按 {@code SO-yyyyMMdd-序号} 连续编号，序号自当前最大值递增。
     *
     * @param requests 创建请求列表
     * @return 新建订单视图对象列表（顺序与入参一致，含回填的主键）
     * @throws BizException 货物不存在（40405）或订单号重复（42209）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<OrderVO> createOrders(List<CreateOrderRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<Long> skuIds = requests.stream()
                .map(CreateOrderRequest::skuId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> skuCodes = skuService.loadCodesByIds(skuIds);
        for (Long skuId : skuIds) {
            if (!skuCodes.containsKey(skuId)) {
                throw new BizException(ErrorCode.SKU_NOT_FOUND, "货物不存在: " + skuId);
            }
        }

        String prefix = "SO-" + LocalDate.now().format(ORDER_NO_DATE) + "-";
        int sequence = nextSequence(prefix);

        // 显式指定的订单号一次性批量查重（不在循环里逐条查库，《代码规范》4.4）
        List<String> explicitNos = requests.stream()
                .map(CreateOrderRequest::orderNo)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (!explicitNos.isEmpty()) {
            List<Order> duplicated = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                    .in(Order::getOrderNo, explicitNos));
            if (!duplicated.isEmpty()) {
                throw new BizException(ErrorCode.ORDER_NO_DUPLICATE,
                        "订单号已存在: " + duplicated.get(0).getOrderNo());
            }
        }

        List<Order> entities = new java.util.ArrayList<>(requests.size());
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (CreateOrderRequest request : requests) {
            String orderNo = StringUtils.hasText(request.orderNo())
                    ? request.orderNo().trim()
                    : prefix + String.format("%03d", sequence++);
            if (!seen.add(orderNo)) {
                throw new BizException(ErrorCode.ORDER_NO_DUPLICATE, "请求内订单号重复: " + orderNo);
            }
            Order entity = new Order();
            entity.setOrderNo(orderNo);
            entity.setSkuId(request.skuId());
            entity.setQuantity(request.quantity());
            entity.setPriority(request.priority());
            entity.setPlacedAt(request.placedAt() == null ? LocalDateTime.now() : request.placedAt());
            entity.setStatus(StringUtils.hasText(request.status()) ? request.status() : STATUS_PENDING);
            entity.setRemark(request.remark());
            entities.add(entity);
        }
        orderMapper.insertBatch(entities);
        log.info("批量创建订单成功 数量={} 首个订单号={}", entities.size(), entities.get(0).getOrderNo());
        return entities.stream()
                .map(entity -> OrderVO.from(entity, skuCodes.get(entity.getSkuId())))
                .toList();
    }

    /**
     * 计算下一个可用序号：该前缀下最大序号 + 1。
     *
     * @param prefix 订单号前缀，如 {@code SO-20260910-}
     * @return 下一个序号
     */
    private int nextSequence(String prefix) {
        Order latest = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .likeRight(Order::getOrderNo, prefix)
                .orderByDesc(Order::getOrderNo)
                .last("LIMIT 1"));
        if (latest == null) {
            return 1;
        }
        try {
            return Integer.parseInt(latest.getOrderNo().substring(prefix.length())) + 1;
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    /**
     * 按 id 查询订单，不存在时抛 40406。
     *
     * @param orderId 订单 id
     * @return 订单实体
     */
    public Order getByIdOrThrow(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    /**
     * 生成订单号：{@code SO-yyyyMMdd-序号}，序号为该前缀下的最大序号 + 1。
     *
     * @return 订单号
     */
    private String nextOrderNo() {
        String prefix = "SO-" + LocalDate.now().format(ORDER_NO_DATE) + "-";
        Order latest = orderMapper.selectOne(Wrappers.<Order>lambdaQuery()
                .likeRight(Order::getOrderNo, prefix)
                .orderByDesc(Order::getOrderNo)
                .last("LIMIT 1"));
        int sequence = 1;
        if (latest != null) {
            String tail = latest.getOrderNo().substring(prefix.length());
            try {
                sequence = Integer.parseInt(tail) + 1;
            } catch (NumberFormatException ignored) {
                sequence = 1;
            }
        }
        return prefix + String.format("%03d", sequence);
    }

    /**
     * 组装订单视图对象（含货物编码）。
     *
     * @param order 订单实体
     * @return 订单视图对象
     */
    private OrderVO toVO(Order order) {
        Map<Long, String> codes = skuService.loadCodesByIds(List.of(order.getSkuId()));
        return OrderVO.from(order, codes.get(order.getSkuId()));
    }
}
