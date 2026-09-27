import type { App, Directive } from 'vue'

import { useAuthStore } from '@/stores/auth'

/**
 * 按钮级权限显隐指令（A-F4，《代码规范》5.3）。
 *
 * 用法：
 * ```html
 * <el-button v-permission="'user:manage'">新建用户</el-button>
 * <el-button v-permission="['sku:manage', 'warehouse:manage']">编辑</el-button>
 * ```
 *
 * 说明：指令只做**显示控制**；即便用户绕过前端发起请求，后端仍会返回 403（FR-RBAC-3）。
 *
 * 负责人：a
 */
const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const auth = useAuthStore()
    const required = binding.value
    if (!required) {
      return
    }
    const codes = Array.isArray(required) ? required : [required]
    if (!auth.hasAnyPermission(codes)) {
      el.parentNode?.removeChild(el)
    }
  },
}

/**
 * 注册全局指令。
 *
 * @param app Vue 应用实例
 */
export function setupPermissionDirective(app: App): void {
  app.directive('permission', permission)
}

export default permission
