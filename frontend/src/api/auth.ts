import { get, post, put } from './http'
import type { LoginRequest, LoginResult, UserInfo } from '@/types/auth'

/**
 * 认证接口（API-001~004）。
 *
 * 负责人：a（A-F1）
 */

/** API-001 登录。 */
export function login(payload: LoginRequest): Promise<LoginResult> {
  return post<LoginResult>('/auth/login', payload)
}

/** API-002 登出。 */
export function logout(): Promise<void> {
  return post<void>('/auth/logout')
}

/** API-003 当前用户信息（含角色与权限）。 */
export function fetchCurrentUser(): Promise<UserInfo> {
  return get<UserInfo>('/auth/me')
}

/** API-004 修改本人密码。 */
export function changePassword(payload: {
  oldPassword: string
  newPassword: string
}): Promise<void> {
  return put<void>('/auth/password', payload)
}
