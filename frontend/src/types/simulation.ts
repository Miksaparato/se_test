/**
 * 入库/出库仿真相关类型（《接口文档》第 12~14 节，API-054 ~ API-062）。
 *
 * 负责人：c
 */

/** 入库策略信息（API-054 响应元素）。 */
export interface StrategyInfo {
  /** 策略标识，API-055 请求的 strategy 取值：random / nearest / zoning / grading / smart / fifo */
  name: string
  displayName: string
  description: string
}

/** 入库仿真明细项（API-055 请求的 items 元素）。 */
export interface InboundItemPayload {
  skuId: number
  quantity: number
}

/** 距离口径标识（COM-6）。 */
export type DistanceMetricName = 'manhattan' | 'euclidean' | 'polyline'

/** 入库仿真请求（API-055 请求体）。 */
export interface InboundSimulationPayload {
  warehouseId: number
  strategy: string
  items: InboundItemPayload[]
  name?: string
  distanceMetric?: DistanceMetricName
  layerHeight?: number
  randomSeed?: number
}

/** 一次入库选位明细（API-055 响应的 assignments 元素）。 */
export interface InboundAssignment {
  skuId: number
  skuCode: string
  skuName: string
  locationId: number
  code: string
  quantity: number
  score: number
  reason: string
  /** 软约束告警（如重货放到高层），无告警时为 null */
  warning: string | null
}

/** 入库仿真结果（API-055 / API-056 响应）。 */
export interface InboundSimulation {
  simulationId: string
  planId: number
  planNo: string | null
  strategy: string
  strategyName: string
  warehouseId: number
  assignmentCount: number
  violations: number
  totalDistance: number
  avgDistance: number
  hotAvgDistance: number
  assignments: InboundAssignment[]
}

/** 清空重置结果（API-057 响应）。 */
export interface InboundResetResult {
  simulationId: string
  removedPlanId: number | null
  reset: boolean
}

/** 出库仿真请求（API-058 请求体）。planId 与 warehouseId 至少给一个。 */
export interface OutboundSimulationPayload {
  planId?: number | null
  warehouseId?: number | null
  orderIds?: number[] | null
  pickingMode?: 'single'
  speed?: number
  distanceMetric?: DistanceMetricName
  layerHeight?: number
}

/** 单条订单的一次取货明细（API-059 响应的 orders[].picks 元素）。 */
export interface OutboundPickItem {
  locationId: number
  code: string
  quantity: number
  distance: number
}

/** 单条订单的出库仿真结果（API-059 响应的 orders 元素）。 */
export interface OutboundOrderResult {
  orderId: number
  orderNo: string
  skuId: number
  skuCode: string | null
  quantity: number
  priority: number
  placedAt: string | null
  picks: OutboundPickItem[]
  distance: number
  warning: string | null
}

/** 拣选路径坐标点（API-061 响应元素）。 */
export interface OutboundPathPoint {
  x: number
  y: number
}

/** 拣选路径（API-061 响应元素，供平面图 drawPath 绘制）。 */
export interface OutboundPath {
  orderId: number
  orderNo: string
  points: OutboundPathPoint[]
}

/** 出库仿真路程统计（API-060 响应，FR-4.2）。 */
export interface OutboundStats {
  totalDistance: number
  orderDistances: number[]
  avgDistance: number
  estimatedTime: number
  orderCount: number
  hotAvgDistance: number
  speed: number
  distanceMetric: string
}

/** 出库仿真结果（API-058 / API-059 响应）。 */
export interface OutboundSimulation {
  simulationId: string
  planId: number | null
  planNo: string | null
  warehouseId: number
  pickingMode: string
  /** 库存来源：plan 按方案占用映射 / actual 按当前真实占用 */
  stockSource: 'plan' | 'actual'
  stats: OutboundStats
  paths: OutboundPath[]
  orders: OutboundOrderResult[]
}

/** 随机订单集生成请求（API-062 请求体）。 */
export interface GenerateOrdersPayload {
  count: number
  skuIds?: number[]
  priorityRange?: number[]
  quantityRange?: number[]
  timeRange?: string[]
  randomSeed?: number
}

/** 随机订单集生成结果（API-062 响应）。 */
export interface GenerateOrdersResult {
  generated: number
  orderIds: number[]
  firstOrderNo: string | null
  randomSeed: number
}
