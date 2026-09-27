import { get, post } from './http'
import type {
  GenerateOrdersPayload,
  GenerateOrdersResult,
  InboundResetResult,
  InboundSimulation,
  InboundSimulationPayload,
  OutboundPath,
  OutboundSimulation,
  OutboundSimulationPayload,
  OutboundStats,
  StrategyInfo,
} from '@/types'

/**
 * 入库/出库仿真接口（API-054 ~ API-062，负责人 c）。
 *
 * 统一走 a 提供的 axios 封装（`./http`），自动携带 `Authorization: Bearer <token>`
 * 并统一处理 401/403/422（《代码规范》5.4、《鉴权中间件规范》第 6 节）。
 */

/** API-054 内置入库策略列表（随机/就近/分区/分级/智能推荐/FIFO）。 */
export function listStrategies(): Promise<StrategyInfo[]> {
  return get<StrategyInfo[]>('/strategies')
}

/** API-055 执行入库仿真，形成库位分配方案。 */
export function simulateInbound(
  payload: InboundSimulationPayload,
): Promise<InboundSimulation> {
  return post<InboundSimulation>('/simulations/inbound', payload)
}

/** API-056 查询入库仿真记录与选位理由。 */
export function getInboundSimulation(id: string): Promise<InboundSimulation> {
  return get<InboundSimulation>(`/simulations/inbound/${id}`)
}

/** API-057 清空重置，换策略重新仿真（会一并清除该次仿真产出的方案）。 */
export function resetInboundSimulation(id: string): Promise<InboundResetResult> {
  return post<InboundResetResult>(`/simulations/inbound/${id}/reset`)
}

/** API-058 执行出库仿真（按单拣选）。 */
export function simulateOutbound(
  payload: OutboundSimulationPayload,
): Promise<OutboundSimulation> {
  return post<OutboundSimulation>('/simulations/outbound', payload)
}

/** API-059 查询出库仿真记录。 */
export function getOutboundSimulation(id: string): Promise<OutboundSimulation> {
  return get<OutboundSimulation>(`/simulations/outbound/${id}`)
}

/** API-060 查询路程统计（总/平均/耗时估算）。 */
export function getOutboundStats(id: string): Promise<OutboundStats> {
  return get<OutboundStats>(`/simulations/outbound/${id}/stats`)
}

/** API-061 查询拣选路径坐标（供平面图绘制）。 */
export function getOutboundPath(id: string): Promise<OutboundPath[]> {
  return get<OutboundPath[]>(`/simulations/outbound/${id}/path`)
}

/** API-062 生成随机测试订单集（落库为 pending 订单，可直接喂给 API-058）。 */
export function generateOrders(
  payload: GenerateOrdersPayload,
): Promise<GenerateOrdersResult> {
  return post<GenerateOrdersResult>('/orders/generate', payload)
}
