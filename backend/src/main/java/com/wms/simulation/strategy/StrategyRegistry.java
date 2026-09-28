package com.wms.simulation.strategy;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略注册表（C-B1，插件式设计）。
 *
 * <p>Spring 启动时注入全部 {@link InboundStrategy} 实现并按 {@link InboundStrategy#name()}
 * 建索引；新增策略只需新增一个 {@code @Component} 实现类，本类与仿真引擎无需改动（NFR-3）。
 *
 * @author c
 */
@Component
public class StrategyRegistry {

    private final Map<String, InboundStrategy> strategies;

    /**
     * 构造注册表。
     *
     * @param all Spring 容器中的全部策略实现
     */
    public StrategyRegistry(List<InboundStrategy> all) {
        Map<String, InboundStrategy> map = new LinkedHashMap<>();
        all.stream()
                .sorted(Comparator.comparing(InboundStrategy::name))
                .forEach(strategy -> map.put(strategy.name(), strategy));
        this.strategies = Map.copyOf(map);
    }

    /**
     * 按标识取策略。
     *
     * @param name 策略标识，如 {@code smart}
     * @return 策略实现
     * @throws BizException 标识为空或不存在时抛 40001，并在提示里列出可用策略
     */
    public InboundStrategy get(String name) {
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "策略不能为空，可选：" + names());
        }
        InboundStrategy strategy = strategies.get(name.trim());
        if (strategy == null) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "不支持的入库策略：" + name + "，可选：" + names());
        }
        return strategy;
    }

    /**
     * 全部策略（按标识排序，供 API-054 返回）。
     *
     * @return 策略列表
     */
    public List<InboundStrategy> list() {
        return strategies.values().stream()
                .sorted(Comparator.comparing(InboundStrategy::name))
                .toList();
    }

    /**
     * 已注册的策略标识，逗号分隔。
     *
     * @return 形如 {@code fifo, grading, nearest, random, smart, zoning}
     */
    public String names() {
        return String.join(", ", strategies.keySet().stream().sorted().toList());
    }
}
