package com.wms.recommend.compare;

import com.wms.recommend.compare.dto.PlanMetric;
import com.wms.recommend.compare.service.SuggestionGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 优化建议生成单元测试（T-1）。 */
class SuggestionGeneratorTest {

    @Test
    void should_generate_improvement_suggestion() {
        List<PlanMetric> metrics = List.of(
                new PlanMetric(10L, "随机分配", "随机分配", 5820, 116.4, 210.3, 5),
                new PlanMetric(11L, "就近分配", "就近分配", 4310, 86.2, 98.1, 0));

        String s = SuggestionGenerator.generate(metrics);

        assertTrue(s.contains("下降"), "应提示下降幅度");
        assertTrue(s.contains("就近分配"), "应建议采用更优方案");
    }

    @Test
    void should_note_violations() {
        List<PlanMetric> metrics = List.of(
                new PlanMetric(10L, "随机分配", "随机分配", 5820, 116.4, 210.3, 5));

        String s = SuggestionGenerator.generate(metrics);

        assertTrue(s.contains("重货层高违规"), "应提示违规");
    }

    @Test
    void should_handle_empty() {
        assertTrue(SuggestionGenerator.generate(List.of()).contains("无可用方案"));
    }
}
