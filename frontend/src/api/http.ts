import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types'

/**
 * 统一 axios 实例（对应《代码规范》5.4）。
 * baseURL 指向 /api/v1，开发期由 Vite 代理到后端 8080。
 */
const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

// 请求拦截：预留鉴权头。
// TODO(a): 接入登录（A-B8）后，从 authStore 读取 token 填入 Authorization 头。
http.interceptors.request.use((config) => {
  // config.headers.Authorization = `Bearer ${getToken()}`
  return config
})

// 响应拦截：统一处理网络层错误（HTTP 状态码）。
// 401 跳登录、403 提示无权限、422 提示业务错误（对应《代码规范》4.2 错误码表）。
http.interceptors.response.use(
  (resp) => resp,
  (error) => {
    const status: number | undefined = error.response?.status
    const message: string =
      error.response?.data?.message ?? error.message ?? '请求失败，请稍后重试'
    if (status === 401) {
      // TODO(a): 跳转登录页
    }
    return Promise.reject(new Error(message))
  },
)

/** 业务错误：统一响应 code != 0 时抛出。 */
export class ApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

/**
 * 发送请求并解包统一响应：code == 0 时返回 data 字段，否则抛 {@link ApiError}。
 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const resp = await http.request<ApiResponse<T>>(config)
  const body = resp.data
  if (body.code !== 0) {
    throw new ApiError(body.code, body.message || `业务错误(${body.code})`)
  }
  return body.data
}

export default http
