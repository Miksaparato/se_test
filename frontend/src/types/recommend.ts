/**
 * 库位智能推荐与方案对比相关类型（《接口文档》第 9~11 节）。
 *
 * 负责人：b
 *
 * 说明：本文件由原 `types/index.ts` 拆分而来——`types/index.ts` 现在是统一出口
 * （re-export），各模块类型各归其文件，避免「一个文件里塞所有人的类型」。
 */

/** 货物摘要（API-041 响应的 sku 字段）。 */
export interface SkuBrief {
  id: number
  skuCode: string
  name: string
  weight: number
  turnoverRate: number
  priority: number
  category: string
}

/** 各分项加权得分（LocationScore.subScores）。 */
export interface SubScores {
  weight: number
  freq: number
  priority: number
  other: number
}

/** 单个库位评分（API-041 响应的 candidates 元素）。 */
export interface LocationScore {
  locationId: number
  code: string
  score: number
  subScores: SubScores
  reason: string
}

/** 推荐结果（API-041/042 响应）。 */
export interface RecommendationDto {
  recommendationId: string
  sku: SkuBrief
  candidates: LocationScore[]
}

/** 推荐请求（API-041 请求体）。 */
export interface RecommendRequest {
  skuId: number
  warehouseId: number
  topN?: number
}

/** 采用推荐结果（API-043 响应）。 */
export interface AdoptResult {
  locationId: number
  code: string
  status: string
  score: number
}

/** 评分权重配置（API-044/045）。 */
export interface ScoreWeightConfig {
  weight: number
  freq: number
  priority: number
  other: number
}

/** 库位分层规则配置（API-046/047）。 */
export interface StorageRuleConfig {
  heavyWeightThreshold: number
  maxLayerForHeavy: number
  maxWeightNorm: number
  maxTurnover: number
  maxPriority: number
  goldenZoneRadius: number
  categoryMatchEnabled: boolean
}

/** 参数校准请求（API-048 请求体）。权重/规则可空，空则用当前配置。 */
export interface CalibrateRequest {
  skuId: number
  warehouseId: number
  topN?: number
  weights?: ScoreWeightConfig
  rules?: StorageRuleConfig
}

/** 参数校准结果（API-048 响应）。 */
export interface CalibrateResult {
  weights: ScoreWeightConfig
  rules: StorageRuleConfig
  candidates: LocationScore[]
}

/** 库位占用明细（plans.location_map 的值，《数据库设计说明书》2.6 冻结结构）。 */
export interface LocationOccupancy {
  skuId: number
  quantity: number
}

/**
 * 方案仿真统计结果（plans.result JSON 列，冻结结构）。
 *
 * 两段写入：入库仿真写布局口径，出库仿真覆盖为订单口径（见《T-6-仿真与距离口径说明.md》第 4 节）。
 */
export interface PlanResult {
  totalDistance: number
  avgDistance: number
  hotAvgDistance: number
  violations: number
  orderCount: number
  estimatedTime: number
}

/** 分配方案（API-049/050 响应，与后端 Plan 实体一致，c 写 b 读）。 */
export interface Plan {
  id: number
  planNo: string | null
  name: string | null
  warehouseId: number | null
  simulationId: string | null
  strategy: string
  strategyName: string | null
  params: Record<string, unknown> | null
  locationMap: Record<string, LocationOccupancy>
  result: PlanResult
  totalDistance: number
  avgDistance: number
  hotAvgDistance: number
  violations: number
  orderCount: number
  estimatedTime: number
  createdBy: number | null
  createdAt: string | null
}

/** 方案指标（API-051 响应的 metrics 元素）。 */
export interface PlanMetric {
  planId: number
  name: string
  strategy: string
  totalDistance: number
  avgDistance: number
  hotAvgDistance: number
  violations: number
}

/** 多方案对比请求（API-051 请求体）。 */
export interface CompareRequest {
  planIds: number[]
}

/** 多方案对比结果（API-051 响应）。 */
export interface CompareResult {
  metrics: PlanMetric[]
  suggestion: string
}
