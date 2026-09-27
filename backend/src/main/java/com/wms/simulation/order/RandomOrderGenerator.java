package com.wms.simulation.order;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.data.order.OrderService;
import com.wms.data.order.dto.CreateOrderRequest;
import com.wms.data.order.vo.OrderVO;
import com.wms.domain.entity.Sku;
import com.wms.domain.repository.WarehouseDataRepository;
import com.wms.simulation.order.dto.GenerateOrdersRequest;
import com.wms.simulation.order.dto.GenerateOrdersResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 随机测试订单集生成器（C-B8 / FR-1.3，对应 API-062）。
 *
 * <p>用途：出库仿真（FR-4）需要一批规模可控、优先级与下达时间有分布的订单；
 * 手工录入既慢又难以复现实验。本生成器按给定范围随机生成订单并**落库为
 * {@code pending} 订单**，随后可直接用 API-058 做拣选仿真。
 *
 * <p>可复现性：请求里带 {@code randomSeed} 时，同一仓库 + 同一参数 + 同一种子
 * 必然生成同一批订单，便于报告里标注实验条件、也便于回归测试。
 *
 * <p>落库走 a 的公开 Service（{@link OrderService#createOrders}），内部是一条批量插入 SQL，
 * 不在循环里逐条写库（《代码规范》4.4）。
 *
 * @author c
 */
@Service
public class RandomOrderGenerator {

    private static final Logger log = LoggerFactory.getLogger(RandomOrderGenerator.class);

    /** 默认优先级范围（与 {@code orders.priority} 的 1~5 取值一致）。 */
    private static final int DEFAULT_PRIORITY_MIN = 1;
    private static final int DEFAULT_PRIORITY_MAX = 5;

    /** 默认出库数量范围。 */
    private static final int DEFAULT_QUANTITY_MIN = 1;
    private static final int DEFAULT_QUANTITY_MAX = 20;

    /** 默认时间跨度（天）：下达时间落在「今天 ~ 今天+N 天」内。 */
    private static final int DEFAULT_TIME_SPAN_DAYS = 7;

    private final WarehouseDataRepository repository;
    private final OrderService orderService;

    /**
     * 构造生成器。
     *
     * @param repository   基础数据仓储（取可选货物）
     * @param orderService a 的订单服务（批量落库）
     */
    public RandomOrderGenerator(WarehouseDataRepository repository, OrderService orderService) {
        this.repository = repository;
        this.orderService = orderService;
    }

    /**
     * 生成并落库随机订单集。
     *
     * @param request 生成请求
     * @return 生成结果（含订单 id 列表与随机种子）
     * @throws BizException 请求参数非法（40001）或指定的货物不存在（40405）
     */
    @Transactional(rollbackFor = Exception.class)
    public GenerateOrdersResult generate(GenerateOrdersRequest request) {
        if (request == null || request.count() == null || request.count() < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "生成条数必须大于 0");
        }
        List<Sku> candidates = resolveCandidates(request.skuIds());
        if (candidates.isEmpty()) {
            throw new BizException(ErrorCode.SKU_NOT_FOUND, "没有可用的货物，请先录入 SKU");
        }

        int[] priority = resolveRange(request.priorityRange(), DEFAULT_PRIORITY_MIN, DEFAULT_PRIORITY_MAX, "优先级");
        int[] quantity = resolveRange(request.quantityRange(), DEFAULT_QUANTITY_MIN, DEFAULT_QUANTITY_MAX, "数量");
        LocalDate[] window = resolveTimeRange(request.timeRange());
        long seed = request.randomSeed() == null ? System.nanoTime() : request.randomSeed();
        Random random = new Random(seed);

        long spanSeconds = Math.max(1,
                java.time.Duration.between(window[0].atStartOfDay(), window[1].atTime(LocalTime.MAX)).getSeconds());

        List<CreateOrderRequest> requests = new ArrayList<>(request.count());
        for (int i = 0; i < request.count(); i++) {
            Sku sku = candidates.get(random.nextInt(candidates.size()));
            int qty = nextInt(random, quantity[0], quantity[1]);
            int prio = nextInt(random, priority[0], priority[1]);
            LocalDateTime placedAt = window[0].atStartOfDay().plusSeconds(random.nextLong(spanSeconds));
            requests.add(new CreateOrderRequest(null, sku.getId(), qty, prio, placedAt, null, "随机订单集生成"));
        }

        List<OrderVO> created = orderService.createOrders(requests);
        List<Long> ids = created.stream().map(OrderVO::id).toList();
        log.info("随机订单集生成完成 条数={} 种子={} 货物种类={}", created.size(), seed, candidates.size());
        return new GenerateOrdersResult(created.size(), ids,
                created.isEmpty() ? null : created.get(0).orderNo(), seed);
    }

    /**
     * 解析参与随机的货物：显式给 skuIds 就逐个校验存在性，否则取全部货物。
     *
     * @param skuIds 货物 id 列表，可为空
     * @return 货物列表
     */
    private List<Sku> resolveCandidates(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return repository.listSkus();
        }
        List<Sku> skus = new ArrayList<>(skuIds.size());
        for (Long id : skuIds.stream().distinct().toList()) {
            skus.add(repository.findSku(id).orElseThrow(
                    () -> new BizException(ErrorCode.SKU_NOT_FOUND, "货物不存在: " + id)));
        }
        return skus;
    }

    /**
     * 解析形如 {@code [min, max]} 的范围，缺省时用默认值；非法时抛 40001。
     *
     * @param range    范围数组
     * @param fallbackMin 默认下界
     * @param fallbackMax 默认上界
     * @param label    字段名（用于错误提示）
     * @return 长度为 2 的数组 {@code [min, max]}
     */
    private int[] resolveRange(List<Integer> range, int fallbackMin, int fallbackMax, String label) {
        if (range == null || range.isEmpty()) {
            return new int[]{fallbackMin, fallbackMax};
        }
        if (range.size() != 2 || range.get(0) == null || range.get(1) == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "范围必须形如 [min, max]");
        }
        int min = range.get(0);
        int max = range.get(1);
        if (min > max || min < 1) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    label + "范围非法：需满足 1 ≤ min ≤ max，收到 [" + min + ", " + max + "]");
        }
        return new int[]{min, max};
    }

    /**
     * 解析下达时间范围 {@code [起, 止]}，缺省为「今天 ~ 今天 + 7 天」。
     *
     * @param range 日期范围
     * @return 长度为 2 的数组 {@code [起, 止]}
     */
    private LocalDate[] resolveTimeRange(List<LocalDate> range) {
        if (range == null || range.size() != 2 || range.get(0) == null || range.get(1) == null) {
            LocalDate today = LocalDate.now();
            return new LocalDate[]{today, today.plusDays(DEFAULT_TIME_SPAN_DAYS)};
        }
        if (range.get(0).isAfter(range.get(1))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "时间范围的起止顺序颠倒");
        }
        return new LocalDate[]{range.get(0), range.get(1)};
    }

    /**
     * 生成 {@code [min, max]} 闭区间内的随机整数。
     *
     * @param random 随机源
     * @param min    下界
     * @param max    上界
     * @return 随机整数
     */
    private int nextInt(Random random, int min, int max) {
        return min == max ? min : min + random.nextInt(max - min + 1);
    }
}
