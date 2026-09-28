package com.wms.simulation;

import com.wms.common.BizException;
import com.wms.common.ErrorCode;
import com.wms.simulation.inbound.dto.InboundSimulationDto;
import com.wms.simulation.outbound.dto.OutboundSimulationDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 仿真会话存储（c 模块自有状态）。
 *
 * <p>按《数据库设计说明书》2.6 的约定：「方案不绑定 simulation 表，仿真明细
 * （每次选位与理由、拣选路径）由 c 在 {@code simulation/} 模块内保存，方案只落库汇总快照」。
 * 因此这里用进程内 Map 保存仿真明细，**入库方案的汇总快照与库位占用映射落 {@code plans} 表**。
 *
 * <p>影响：应用重启后，历史仿真明细（API-056/059/060/061 的查询）会失效并返回 40411，
 * 但方案与方案对比（API-049~053）不受影响——这是刻意取舍，避免为过程数据引入新表。
 *
 * @author c
 */
@Component
public class SimulationStore {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Map<String, InboundSimulationDto> inboundRecords = new ConcurrentHashMap<>();
    private final Map<String, OutboundSimulationDto> outboundRecords = new ConcurrentHashMap<>();
    private final AtomicLong inboundSeq = new AtomicLong();
    private final AtomicLong outboundSeq = new AtomicLong();

    /**
     * 生成下一个入库仿真单号，形如 {@code INB-001}（《接口文档》API-055 示例口径）。
     *
     * @return 入库仿真单号
     */
    public String nextInboundId() {
        return String.format("INB-%03d", inboundSeq.incrementAndGet());
    }

    /**
     * 生成下一个出库仿真单号，形如 {@code OUT-001}（《接口文档》API-058 示例口径）。
     *
     * @return 出库仿真单号
     */
    public String nextOutboundId() {
        return String.format("OUT-%03d", outboundSeq.incrementAndGet());
    }

    /**
     * 方案编号前缀（按日期分片），如 {@code PLAN-20260927-}。
     *
     * <p>方案编号**不在这里自增**：{@code plans.plan_no} 有唯一键，进程内计数器一重启就从 1
     * 重来会直接撞库，因此序号由 {@code InboundSimulationService} 查库取最大值后 +1。
     *
     * @return 今日方案编号前缀
     */
    public String currentPlanNoPrefix() {
        return "PLAN-" + LocalDate.now().format(DATE) + "-";
    }

    /**
     * 保存入库仿真记录。
     *
     * @param dto 记录
     * @return 记录
     */
    public InboundSimulationDto saveInbound(InboundSimulationDto dto) {
        inboundRecords.put(dto.simulationId(), dto);
        return dto;
    }

    /**
     * 查询入库仿真记录，不存在时抛 40411。
     *
     * @param simulationId 仿真单号
     * @return 记录
     */
    public InboundSimulationDto getInbound(String simulationId) {
        InboundSimulationDto dto = inboundRecords.get(simulationId);
        if (dto == null) {
            throw new BizException(ErrorCode.SIMULATION_NOT_FOUND,
                    "入库仿真记录不存在或已重置：" + simulationId);
        }
        return dto;
    }

    /**
     * 删除入库仿真记录（「清空重置」，API-057）。
     *
     * @param simulationId 仿真单号
     * @return 被删除的记录，不存在时为空
     */
    public Optional<InboundSimulationDto> removeInbound(String simulationId) {
        return Optional.ofNullable(inboundRecords.remove(simulationId));
    }

    /**
     * 保存出库仿真记录。
     *
     * @param dto 记录
     * @return 记录
     */
    public OutboundSimulationDto saveOutbound(OutboundSimulationDto dto) {
        outboundRecords.put(dto.simulationId(), dto);
        return dto;
    }

    /**
     * 查询出库仿真记录，不存在时抛 40411。
     *
     * @param simulationId 仿真单号
     * @return 记录
     */
    public OutboundSimulationDto getOutbound(String simulationId) {
        OutboundSimulationDto dto = outboundRecords.get(simulationId);
        if (dto == null) {
            throw new BizException(ErrorCode.SIMULATION_NOT_FOUND,
                    "出库仿真记录不存在：" + simulationId);
        }
        return dto;
    }

    /**
     * 列出全部入库仿真记录（按单号倒序，供前端「仿真历史」展示）。
     *
     * @return 记录列表
     */
    public List<InboundSimulationDto> listInbound() {
        return inboundRecords.values().stream()
                .sorted(Comparator.comparing(InboundSimulationDto::simulationId).reversed())
                .toList();
    }

    /**
     * 列出全部出库仿真记录（按单号倒序）。
     *
     * @return 记录列表
     */
    public List<OutboundSimulationDto> listOutbound() {
        return outboundRecords.values().stream()
                .sorted(Comparator.comparing(OutboundSimulationDto::simulationId).reversed())
                .toList();
    }
}
