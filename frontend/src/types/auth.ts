/**
 * 认证与 RBAC 相关类型（《接口文档》第 3~4 节）。
 *
 * 负责人：a
 */

/** 权限码（《接口文档》1.4 清单，禁止自行发明）。 */
export type PermissionCode =
  | 'user:manage'
  | 'warehouse:manage'
  | 'sku:manage'
  | 'recommend:view'
  | 'sim:run'
  | 'sim:view'
  | 'compare:view'
  | 'report:export'
  | 'config:manage'

/** 账号状态。 */
export type UserStatus = 'active' | 'disabled'

/** 登录请求。 */
export interface LoginRequest {
  account: string
  password: string
}

/** 登录用户信息。 */
export interface UserInfo {
  id: number
  account: string
  name: string | null
  email: string | null
  phone: string | null
  status: UserStatus
  roles: string[]
  permissions: PermissionCode[]
  lastLoginAt: string | null
  remark: string | null
  createdAt: string | null
}

/** 登录响应（API-001）。 */
export interface LoginResult {
  token: string
  tokenType: string
  expiresIn: number
  user: UserInfo
}

/** 用户列表项（API-006）。 */
export interface UserItem {
  id: number
  account: string
  name: string | null
  email: string | null
  phone: string | null
  status: UserStatus
  roles: string[]
  lastLoginAt: string | null
  remark: string | null
  createdAt: string | null
}

/** 创建用户请求（API-005）。 */
export interface CreateUserPayload {
  account: string
  password: string
  name?: string
  email?: string
  phone?: string
  status?: UserStatus
  roles?: string[]
  remark?: string
}

/** 更新用户请求（API-007）。 */
export interface UpdateUserPayload {
  name?: string
  email?: string
  phone?: string
  remark?: string
  roles?: string[]
}

/** 角色（API-010）。 */
export interface RoleInfo {
  id: number
  code: string
  name: string
  description: string | null
  isBuiltin: boolean
  userCount: number
  permissions: PermissionCode[] | null
  createdAt: string | null
}

/** 创建/更新角色请求。 */
export interface RolePayload {
  code?: string
  name?: string
  description?: string
}

/** 权限项（API-015）。 */
export interface PermissionInfo {
  id: number
  code: PermissionCode
  name: string
  type: 'menu' | 'action'
  sortNo: number
  remark: string | null
}

/** 用户列表查询参数。 */
export interface UserQuery {
  page?: number
  page_size?: number
  status?: UserStatus | ''
  keyword?: string
}
