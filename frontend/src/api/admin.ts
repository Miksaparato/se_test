import { del, get, post, put } from './http'
import type { PageResult } from '@/types/api'
import type {
  CreateUserPayload,
  PermissionInfo,
  RoleInfo,
  RolePayload,
  UpdateUserPayload,
  UserInfo,
  UserItem,
  UserQuery,
  UserStatus,
} from '@/types/auth'

/**
 * 用户 / 角色 / 权限接口（API-005~015，均需 user:manage）。
 *
 * 负责人：a（A-F3）
 */

/** API-005 创建用户。 */
export function createUser(payload: CreateUserPayload): Promise<UserInfo> {
  return post<UserInfo>('/users', payload)
}

/** API-006 用户列表（分页）。 */
export function listUsers(query: UserQuery): Promise<PageResult<UserItem>> {
  return get<PageResult<UserItem>>('/users', query as Record<string, unknown>)
}

/** API-007 更新用户。 */
export function updateUser(id: number, payload: UpdateUserPayload): Promise<UserInfo> {
  return put<UserInfo>(`/users/${id}`, payload)
}

/** API-008 启用/禁用用户。 */
export function updateUserStatus(id: number, status: UserStatus): Promise<UserInfo> {
  return put<UserInfo>(`/users/${id}/status`, { status })
}

/** API-009 删除用户。 */
export function deleteUser(id: number): Promise<void> {
  return del<void>(`/users/${id}`)
}

/** API-010 角色列表。 */
export function listRoles(): Promise<RoleInfo[]> {
  return get<RoleInfo[]>('/roles')
}

/** API-011 创建角色。 */
export function createRole(payload: RolePayload): Promise<RoleInfo> {
  return post<RoleInfo>('/roles', payload)
}

/** API-012 更新角色。 */
export function updateRole(id: number, payload: RolePayload): Promise<RoleInfo> {
  return put<RoleInfo>(`/roles/${id}`, payload)
}

/** API-013 删除角色。 */
export function deleteRole(id: number): Promise<void> {
  return del<void>(`/roles/${id}`)
}

/** API-014 为角色分配权限（全量覆盖）。 */
export function assignRolePermissions(id: number, permissions: string[]): Promise<RoleInfo> {
  return put<RoleInfo>(`/roles/${id}/permissions`, { permissions })
}

/** API-015 权限清单。 */
export function listPermissions(): Promise<PermissionInfo[]> {
  return get<PermissionInfo[]>('/permissions')
}
