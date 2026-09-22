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
})

export default router
