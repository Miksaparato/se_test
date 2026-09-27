import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/recommend' },
  {
    path: '/recommend',
    name: 'recommend',
    component: () => import('@/views/recommend/RecommendView.vue'),
    meta: { title: '入库推荐', permission: 'recommend:view' },
  },
  {
    path: '/compare',
    name: 'compare',
    component: () => import('@/views/compare/CompareView.vue'),
    meta: { title: '方案对比', permission: 'compare:view' },
  },
  {
    path: '/config',
    name: 'config',
    component: () => import('@/views/recommend/ConfigView.vue'),
    meta: { title: '权重与规则配置', permission: 'config:manage' },
import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

/**
 * 路由与权限守卫（A-F4，《代码规范》5.3）。
 *
 * 约定：路由 `meta.permission` 声明所需权限码；未登录跳登录页，越权跳 403 页。
 * 注意：前端守卫**只是体验优化**，后端 `@PreAuthorize` + 过滤器链才是安全边界。
 */

/** 菜单/面包屑元信息。 */
declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题 */
    title?: string
    /** 所需权限码（《接口文档》1.4） */
    permission?: string
    /** 是否在侧边菜单中隐藏 */
    hidden?: boolean
    /** 是否免登录 */
    anonymous?: boolean
    /** 菜单图标（Element Plus 图标组件名） */
    icon?: string
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录', anonymous: true, hidden: true },
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/overview',
    children: [
      {
        path: 'overview',
        name: 'overview',
        component: () => import('@/views/overview/OverviewView.vue'),
        meta: { title: '仓库总览', icon: 'OfficeBuilding', permission: 'sim:view' },
      },
      {
        path: 'data/warehouses',
        name: 'warehouses',
        component: () => import('@/views/data/WarehouseListView.vue'),
        meta: { title: '仓库管理', icon: 'HomeFilled', permission: 'sim:view' },
      },
      {
        path: 'data/racks',
        name: 'racks',
        component: () => import('@/views/data/RackListView.vue'),
        meta: { title: '货架管理', icon: 'Grid', permission: 'sim:view' },
      },
      {
        path: 'data/locations',
        name: 'locations',
        component: () => import('@/views/data/LocationListView.vue'),
        meta: { title: '库位管理', icon: 'Location', permission: 'sim:view' },
      },
      {
        path: 'data/skus',
        name: 'skus',
        component: () => import('@/views/data/SkuListView.vue'),
        meta: { title: '货物管理', icon: 'Box', permission: 'sim:view' },
      },
      {
        path: 'data/orders',
        name: 'orders',
        component: () => import('@/views/data/OrderListView.vue'),
        meta: { title: '订单管理', icon: 'Document', permission: 'sim:view' },
      },
      {
        path: 'admin/users',
        name: 'users',
        component: () => import('@/views/admin/UserListView.vue'),
        meta: { title: '用户管理', icon: 'User', permission: 'user:manage' },
      },
      {
        path: 'admin/roles',
        name: 'roles',
        component: () => import('@/views/admin/RoleListView.vue'),
        meta: { title: '角色与权限', icon: 'Key', permission: 'user:manage' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/auth/ProfileView.vue'),
        meta: { title: '个人设置', icon: 'Setting', hidden: true },
      },
      {
        path: '403',
        name: 'forbidden',
        component: () => import('@/views/error/ForbiddenView.vue'),
        meta: { title: '无访问权限', hidden: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '页面不存在', anonymous: true, hidden: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局守卫：前端仅做显隐（TODO(a) 接入 authStore 后校验 meta.permission），
// 后端 Spring Security 强制鉴权仍是唯一安全边界（FR-RBAC-3）。
router.beforeEach((to) => {
  document.title = `${String(to.meta.title ?? '')} - 智能仓储仿真系统`
  history: createWebHashHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.anonymous) {
    return true
  }

  if (!auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 刷新页面后恢复用户信息与权限
  const loaded = await auth.ensureLoaded()
  if (!loaded) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  const required = to.meta.permission
  if (required && !auth.hasPermission(required)) {
    return { name: 'forbidden' }
  }

  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 智能仓储仿真系统` : '智能仓储仿真系统'
})

export default router
