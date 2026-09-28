import { get, post } from './http'
import type { AdoptResult, RecommendationDto, RecommendRequest } from '@/types'

/**
 * 库位智能推荐接口（API-041 ~ API-043，负责人 b）。
 *
 * 统一走 a 提供的 axios 封装（`./http`），自动携带 `Authorization: Bearer <token>`
 * 并统一处理 401/403/422（《代码规范》5.4、《鉴权中间件规范》第 6 节）。
 */

/** API-041 计算推荐：对空闲库位评分并排序。 */
export function recommend(req: RecommendRequest): Promise<RecommendationDto> {
  return post<RecommendationDto>('/recommendations', req)
}

/** API-042 查询推荐结果。 */
export function getRecommendation(id: string): Promise<RecommendationDto> {
  return get<RecommendationDto>(`/recommendations/${id}`)
}

/** API-043 一键采用推荐（更新库位占用状态）。 */
export function adoptRecommendation(id: string): Promise<AdoptResult> {
  return post<AdoptResult>(`/recommendations/${id}/adopt`)
}
