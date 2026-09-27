import { request } from './http'
import type { AdoptResult, RecommendationDto, RecommendRequest } from '@/types'

/** API-041 计算推荐：对空闲库位评分并排序。 */
export function recommend(req: RecommendRequest): Promise<RecommendationDto> {
  return request<RecommendationDto>({ method: 'POST', url: '/recommendations', data: req })
}

/** API-042 查询推荐结果。 */
export function getRecommendation(id: string): Promise<RecommendationDto> {
  return request<RecommendationDto>({ method: 'GET', url: `/recommendations/${id}` })
}

/** API-043 一键采用推荐（更新库位占用状态）。 */
export function adoptRecommendation(id: string): Promise<AdoptResult> {
  return request<AdoptResult>({ method: 'POST', url: `/recommendations/${id}/adopt` })
}
