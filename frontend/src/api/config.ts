import { request } from './http'
import type {
  CalibrateRequest,
  CalibrateResult,
  ScoreWeightConfig,
  StorageRuleConfig,
} from '@/types'

/** API-044 获取评分权重配置。 */
export function getWeights(): Promise<ScoreWeightConfig> {
  return request<ScoreWeightConfig>({ method: 'GET', url: '/config/weights' })
}

/** API-045 更新评分权重（之和须为 1）。 */
export function updateWeights(req: ScoreWeightConfig): Promise<ScoreWeightConfig> {
  return request<ScoreWeightConfig>({ method: 'PUT', url: '/config/weights', data: req })
}

/** API-046 获取库位分层规则。 */
export function getRules(): Promise<StorageRuleConfig> {
  return request<StorageRuleConfig>({ method: 'GET', url: '/config/rules' })
}

/** API-047 更新分层规则（如「第 1 层放重货」）。 */
export function updateRules(req: StorageRuleConfig): Promise<StorageRuleConfig> {
  return request<StorageRuleConfig>({ method: 'PUT', url: '/config/rules', data: req })
}

/** API-048 参数校准：按新参数重新评分（预览，不落库）。 */
export function calibrate(req: CalibrateRequest): Promise<CalibrateResult> {
  return request<CalibrateResult>({ method: 'POST', url: '/config/calibrate', data: req })
}
