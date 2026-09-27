package com.wms.recommend.compare.service;

import com.wms.recommend.compare.dto.PlanMetric;

import java.util.List;

/**
 * 对比报告导出器（B-B7 / FR-5.2）：支持 Markdown 与 CSV 两种格式（需求为「任选其一即可」）。
 * 纯函数实现（NFR-4），便于单测。
 */
public final class ReportExporter {

    private ReportExporter() {
    }

    public static String export(List<PlanMetric> metrics, String suggestion, String format) {
        if ("csv".equalsIgnoreCase(format)) {
            return toCsv(metrics, suggestion);
        }
        return toMarkdown(metrics, suggestion);
    }

    private static String toMarkdown(List<PlanMetric> metrics, String suggestion) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 库位分配方案对比报告\n\n");
        sb.append("| 方案 | 策略 | 总搬运路程 | 平均路程 | 高频货平均距离 | 重货违规数 |\n");
        sb.append("| --- | --- | ---: | ---: | ---: | ---: |\n");
        for (PlanMetric m : metrics) {
            sb.append(String.format("| %s | %s | %.1f | %.1f | %.1f | %d |\n",
                    m.name(), m.strategy(), m.totalDistance(), m.avgDistance(),
                    m.hotAvgDistance(), m.violations()));
        }
        sb.append("\n## 优化建议\n\n").append(suggestion).append("\n");
        return sb.toString();
    }

    private static String toCsv(List<PlanMetric> metrics, String suggestion) {
        StringBuilder sb = new StringBuilder();
        sb.append("方案,策略,总搬运路程,平均路程,高频货平均距离,重货违规数\n");
        for (PlanMetric m : metrics) {
            sb.append(String.format("%s,%s,%.1f,%.1f,%.1f,%d\n",
                    m.name(), m.strategy(), m.totalDistance(), m.avgDistance(),
                    m.hotAvgDistance(), m.violations()));
        }
        sb.append("\n优化建议,\"").append(suggestion.replace("\"", "'")).append("\"\n");
        return sb.toString();
    }
}
