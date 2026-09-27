/**
 * 前端 TS 类型，与后端 DTO / 《接口文档》响应结构一一对应（禁止私自改名）。
 * 参考：com.wms.recommend.dto / engine / compare.dto / config.dto
 */

/** 统一响应封装（com.wms.common.ApiResponse）。 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

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

/** 分配方案（API-049/050，字段与 Plan 实体一致，c 写 b 读）。 */
export interface Plan {
  id: number
  name: string
  strategy: string
  totalDistance: number
  avgDistance: number
  hotAvgDistance: number
  violations: number
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
