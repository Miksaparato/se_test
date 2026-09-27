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
import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

import type { ApiResponse } from '@/types/api'

/**
 * axios 统一封装（《代码规范》5.4、《鉴权中间件规范》第 6 节）。
 *
 * 职责：
 *  1. 注入 `Authorization: Bearer <token>`；
 *  2. 统一处理 401（清除登录态并跳登录页）与 403（提示无权限）；
 *  3. 业务错误码非 0 时统一弹提示并 reject，业务代码无需重复判错。
 *
 * 负责人：a（A-F4）
 */

/** Token 在 localStorage 中的键名。 */
export const TOKEN_KEY = 'wms_token'

/** 读取本地 Token。 */
export function getToken(): string {
  return localStorage.getItem(TOKEN_KEY) ?? ''
}

/** 写入本地 Token。 */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 清除本地 Token。 */
export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

const http: AxiosInstance = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/v1`,
  timeout: 60000,
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 401 时避免重复跳转与重复提示。 */
let redirecting = false

http.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<unknown>>) => {
    const body = response.data
    // 文件下载等非统一结构响应直接放行
    if (body == null || typeof body !== 'object' || !('code' in body)) {
      return response
    }
    if (body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return response
  },
  (error) => {
    const status: number | undefined = error?.response?.status
    const message: string = error?.response?.data?.message ?? error.message ?? '网络异常'

    if (status === 401) {
      clearToken()
      if (!redirecting) {
        redirecting = true
        ElMessage.error(message || '登录已失效，请重新登录')
        const redirect = encodeURIComponent(window.location.hash.replace(/^#/, '') || '/')
        window.location.hash = `#/login?redirect=${redirect}`
        window.setTimeout(() => {
          redirecting = false
        }, 1000)
      }
    } else if (status === 403) {
      // 越权：后端强制鉴权是唯一安全边界，前端仅做提示（FR-RBAC-5）
      ElMessage.error(message || '无该操作权限')
    } else if (status === 400 || status === 404 || status === 422) {
      ElMessage.error(message)
    } else {
      ElMessage.error(message || '系统异常，请稍后重试')
    }
    return Promise.reject(error)
  },
)

/**
 * GET 请求，直接返回业务数据。
 *
 * @param url 相对路径（不含 /api/v1）
 * @param params 查询参数
 */
export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await http.get<ApiResponse<T>>(url, { params })
  return response.data.data
}

/**
 * POST 请求，直接返回业务数据。
 *
 * @param url 相对路径
 * @param body 请求体
 */
export async function post<T>(url: string, body?: unknown): Promise<T> {
  const response = await http.post<ApiResponse<T>>(url, body)
  return response.data.data
}

/**
 * PUT 请求，直接返回业务数据。
 *
 * @param url 相对路径
 * @param body 请求体
 */
export async function put<T>(url: string, body?: unknown): Promise<T> {
  const response = await http.put<ApiResponse<T>>(url, body)
  return response.data.data
}

/**
 * DELETE 请求，直接返回业务数据。
 *
 * @param url 相对路径
 */
export async function del<T>(url: string): Promise<T> {
  const response = await http.delete<ApiResponse<T>>(url)
  return response.data.data
}

/**
 * 上传文件（multipart/form-data）。
 *
 * @param url 相对路径
 * @param file 文件
 * @param extra 额外表单字段
 */
export async function upload<T>(
  url: string,
  file: File,
  extra?: Record<string, string>,
): Promise<T> {
  const form = new FormData()
  form.append('file', file)
  Object.entries(extra ?? {}).forEach(([key, value]) => form.append(key, value))
  const response = await http.post<ApiResponse<T>>(url, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return response.data.data
}

/**
 * 下载文件（导出接口返回文件流，非统一 JSON 结构）。
 *
 * @param url 相对路径
 * @param filename 保存的文件名
 * @param params 查询参数
 */
export async function download(
  url: string,
  filename: string,
  params?: Record<string, unknown>,
): Promise<void> {
  const response = await http.get(url, { params, responseType: 'blob' })
  const blob = new Blob([response.data as unknown as BlobPart])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(link.href)
}

export default http
