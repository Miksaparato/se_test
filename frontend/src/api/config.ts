import { get, post, put } from './http'
import type {
  CalibrateRequest,
  CalibrateResult,
  ScoreWeightConfig,
  StorageRuleConfig,
} from '@/types'

/**
 * 权重 / 分层规则 / 参数校准接口（API-044 ~ API-048，负责人 b）。
 *
 * 统一走 a 提供的 axios 封装（`./http`），自动携带 `Authorization: Bearer <token>`
 * 并统一处理 401/403/422（《代码规范》5.4）。
 */

/** API-044 获取评分权重配置。 */
export function getWeights(): Promise<ScoreWeightConfig> {
  return get<ScoreWeightConfig>('/config/weights')
}

/** API-045 更新评分权重（之和须为 1）。 */
export function updateWeights(req: ScoreWeightConfig): Promise<ScoreWeightConfig> {
  return put<ScoreWeightConfig>('/config/weights', req)
}

/** API-046 获取库位分层规则。 */
export function getRules(): Promise<StorageRuleConfig> {
  return get<StorageRuleConfig>('/config/rules')
}

/** API-047 更新分层规则（如「第 1 层放重货」）。 */
export function updateRules(req: StorageRuleConfig): Promise<StorageRuleConfig> {
  return put<StorageRuleConfig>('/config/rules', req)
}

/** API-048 参数校准：按新参数重新评分（预览，不落库）。 */
export function calibrate(req: CalibrateRequest): Promise<CalibrateResult> {
  return post<CalibrateResult>('/config/calibrate', req)
}
