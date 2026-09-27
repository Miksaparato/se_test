import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { fetchCurrentUser, login as loginApi, logout as logoutApi } from '@/api/auth'
import { clearToken, getToken, setToken } from '@/api/http'
import type { LoginRequest, PermissionCode, UserInfo } from '@/types/auth'

/**
 * 认证状态（Pinia）。
 *
 * 职责（《代码规范》5.5）：缓存当前用户与权限列表，供路由守卫与菜单/按钮显隐使用。
 * 注意：前端显隐**只是体验优化**，后端强制鉴权才是安全边界（FR-RBAC-3）。
 *
 * 负责人：a（A-F1 / A-F4）
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(getToken())
  const user = ref<UserInfo | null>(null)
  const loading = ref(false)

  /** 是否已登录（有 Token 且已加载用户信息）。 */
  const isLoggedIn = computed(() => Boolean(token.value))

  /** 当前权限码集合。 */
  const permissions = computed<PermissionCode[]>(() => user.value?.permissions ?? [])

  /** 当前角色标识集合。 */
  const roles = computed<string[]>(() => user.value?.roles ?? [])

  /**
   * 是否拥有某权限。
   *
   * @param code 权限码
   */
  function hasPermission(code: PermissionCode | string): boolean {
    return permissions.value.includes(code as PermissionCode)
  }

  /** 是否拥有任意一个指定权限。 */
  function hasAnyPermission(codes: Array<PermissionCode | string>): boolean {
    return codes.some((code) => hasPermission(code))
  }

  /**
   * 登录。
   *
   * @param payload 账号密码
   */
  async function login(payload: LoginRequest): Promise<void> {
    const result = await loginApi(payload)
    token.value = result.token
    setToken(result.token)
    user.value = result.user
  }

  /** 登出：调用后端记录审计日志，并清空本地状态（无状态 JWT 的既有取舍）。 */
  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } catch {
      // 登出失败不阻塞前端清理
    } finally {
      reset()
    }
  }

  /** 清空本地登录态。 */
  function reset(): void {
    token.value = ''
    user.value = null
    clearToken()
  }

  /**
   * 确保用户信息已加载（刷新页面后由路由守卫调用）。
   *
   * @returns 加载成功返回 true；Token 失效返回 false
   */
  async function ensureLoaded(): Promise<boolean> {
    if (!token.value) {
      return false
    }
    if (user.value) {
      return true
    }
    loading.value = true
    try {
      user.value = await fetchCurrentUser()
      return true
    } catch {
      reset()
      return false
    } finally {
      loading.value = false
    }
  }

  /**
   * 重新加载当前用户（权限被管理员调整后，重新登录或调用本方法刷新）。
   */
  async function refresh(): Promise<void> {
    if (token.value) {
      user.value = await fetchCurrentUser()
    }
  }

  return {
    token,
    user,
    loading,
    isLoggedIn,
    permissions,
    roles,
    hasPermission,
    hasAnyPermission,
    login,
    logout,
    reset,
    ensureLoaded,
    refresh,
  }
})
