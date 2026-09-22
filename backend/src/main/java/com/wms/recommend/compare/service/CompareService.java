package com.wms.recommend.compare.service;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.domain.entity.Plan;
import com.wms.domain.repository.WarehouseDataRepository;
import com.wms.recommend.compare.dto.CompareResult;
import com.wms.recommend.compare.dto.PlanMetric;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 方案对比服务（B-B5 / FR-5.1）。
 * 对应 API-049（方案列表）、API-050（方案详情）、API-051（多方案对比）、
 * API-052（优化建议）、API-053（报告导出）。方案数据由成员 c 的仿真引擎产出。
 */
@Service
public class CompareService {

    private final WarehouseDataRepository repository;

    public CompareService(WarehouseDataRepository repository) {
        this.repository = repository;
    }

    public List<Plan> listPlans() {
        return repository.listPlans();
    }

    public Plan getPlan(Long id) {
        return repository.findPlan(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "方案不存在: " + id));
    }

    public CompareResult compare(List<Long> planIds) {
        List<PlanMetric> metrics = planIds.stream()
                .map(id -> PlanMetric.from(repository.findPlan(id)
                        .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "方案不存在: " + id))))
                .toList();
        return new CompareResult(metrics, SuggestionGenerator.generate(metrics));
    }

    /** 以指定方案为基线，与其余全部方案对比生成建议。 */
    public String suggest(Long id) {
        return SuggestionGenerator.generate(orderedWithBaseline(id));
    }

    /** 导出对比报告（Markdown/CSV）。 */
    public String export(Long id, String format) {
        List<PlanMetric> ordered = orderedWithBaseline(id);
        String suggestion = SuggestionGenerator.generate(ordered);
        return ReportExporter.export(ordered, suggestion, format);
    }

    /** 目标方案置于首位作为基线，其余方案按原顺序追加。 */
    private List<PlanMetric> orderedWithBaseline(Long id) {
        Plan target = repository.findPlan(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "方案不存在: " + id));
        List<PlanMetric> ordered = new ArrayList<>();
        ordered.add(PlanMetric.from(target));
        for (Plan p : repository.listPlans()) {
            if (!p.getId().equals(id)) {
                ordered.add(PlanMetric.from(p));
            }
        }
        return ordered;
    }
}
