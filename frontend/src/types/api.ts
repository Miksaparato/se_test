/**
 * 统一响应结构（《接口文档》1.2）。
 *
 * 负责人：a
 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 统一分页结构（《接口文档》1.2）。 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  page_size: number
}

/** 分页查询参数。 */
export interface PageQuery {
  page?: number
  page_size?: number
}
