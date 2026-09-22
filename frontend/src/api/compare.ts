import { request } from './http'
import type { CompareRequest, CompareResult, Plan } from '@/types'

/** API-049 分配方案列表（数据由 c 的仿真产生）。 */
export function listPlans(): Promise<Plan[]> {
  return request<Plan[]>({ method: 'GET', url: '/plans' })
}

/** API-050 方案详情。 */
export function getPlan(id: number): Promise<Plan> {
  return request<Plan>({ method: 'GET', url: `/plans/${id}` })
}

/** API-051 多方案指标对比。 */
export function comparePlans(req: CompareRequest): Promise<CompareResult> {
  return request<CompareResult>({ method: 'POST', url: '/plans/compare', data: req })
}

/** API-052 生成优化建议文字。 */
export function getSuggestion(id: number): Promise<string> {
  return request<string>({ method: 'POST', url: `/plans/${id}/suggestions` })
}

/**
 * API-053 导出对比报告（Markdown/CSV）。
 * 该接口返回文件流而非统一 JSON，直接拼接下载地址交给浏览器。
 */
export function exportReportUrl(id: number, format: 'md' | 'csv' = 'md'): string {
  return `/api/v1/plans/${id}/export?format=${format}`
}
