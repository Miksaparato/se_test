import { download, get, post } from './http'
import type { CompareRequest, CompareResult, Plan } from '@/types'

/**
 * 方案对比、优化建议与报告导出接口（API-049 ~ API-053，负责人 b）。
 *
 * 统一走 a 提供的 axios 封装（`./http`），自动携带 `Authorization: Bearer <token>`
 * 并统一处理 401/403/422（《代码规范》5.4）。
 */

/** API-049 分配方案列表（数据由 c 的仿真产生）。 */
export function listPlans(): Promise<Plan[]> {
  return get<Plan[]>('/plans')
}

/** API-050 方案详情。 */
export function getPlan(id: number): Promise<Plan> {
  return get<Plan>(`/plans/${id}`)
}

/** API-051 多方案指标对比。 */
export function comparePlans(req: CompareRequest): Promise<CompareResult> {
  return post<CompareResult>('/plans/compare', req)
}

/** API-052 生成优化建议文字。 */
export function getSuggestion(id: number): Promise<string> {
  return post<string>(`/plans/${id}/suggestions`)
}

/**
 * API-053 导出对比报告（Markdown/CSV）。
 *
 * 该接口返回文件流而非统一 JSON（`Content-Disposition: attachment`），
 * 因此不能走统一封装的 JSON 解包，改为由 `download()` 取 blob 保存。
 */
export function exportReport(id: number, format: 'md' | 'csv' = 'md'): Promise<void> {
  return download(`/plans/${id}/export`, `plan-${id}.${format}`, { format })
}
