<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'

import { useAuthStore } from '@/stores/auth'

/**
 * 主框架：顶部栏 + 侧边菜单 + 内容区。
 *
 * 菜单按 `meta.permission` 过滤（A-F4）：不同角色登录后只看到被授权的功能。
 *
 * 负责人：a
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

interface MenuItem {
  path: string
  title: string
  icon: string
}

/** 分组菜单：基础数据 / 系统管理。 */
const menuGroups = computed(() => {
  const records = router.getRoutes()
  const pick = (prefix: string): MenuItem[] =>
    records
      .filter((record) => record.path.startsWith(prefix) && record.meta?.title && !record.meta?.hidden)
      .filter((record) => !record.meta?.permission || auth.hasPermission(record.meta.permission))
      .map((record) => ({
        path: record.path,
        title: record.meta.title as string,
        icon: (record.meta.icon as string) ?? 'Menu',
      }))

  const groups = [
    { title: '概览', items: pick('/overview') },
    { title: '基础数据', items: pick('/data/') },
    { title: '系统管理', items: pick('/admin/') },
  ]
  return groups.filter((group) => group.items.length > 0)
})

const activeMenu = computed(() => route.path)

const displayName = computed(() => auth.user?.name || auth.user?.account || '未登录')

const roleLabel = computed(() => auth.roles.join(' / ') || '无角色')

/** 退出登录。 */
async function handleLogout(): Promise<void> {
  await ElMessageBox.confirm('确认退出登录？', '提示', {
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await auth.logout()
  await router.push({ name: 'login' })
}
</script>

<template>
  <el-container class="layout">
    <el-aside class="layout__aside" :width="'var(--wms-sidebar-width)'">
      <div class="layout__brand">智能仓储仿真</div>
      <el-menu :default-active="activeMenu" router class="layout__menu">
        <template v-for="group in menuGroups" :key="group.title">
          <div class="layout__group">{{ group.title }}</div>
          <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout__header">
        <div class="layout__title">{{ route.meta.title ?? '' }}</div>
        <div class="layout__user">
          <el-tag size="small" type="info">{{ roleLabel }}</el-tag>
          <el-dropdown>
            <span class="layout__username">
              {{ displayName }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push({ name: 'profile' })">
                  个人设置
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout__main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.layout__aside {
  background-color: #001529;
  overflow-y: auto;
}

.layout__brand {
  height: var(--wms-header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 1px;
}

.layout__menu {
  border-right: none;
  background-color: #001529;
}

.layout__menu :deep(.el-menu-item) {
  color: #b7c0cd;
}

.layout__menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background-color: var(--wms-primary);
}

.layout__group {
  padding: 12px 20px 4px;
  font-size: 12px;
  color: #6b7785;
}

.layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.layout__title {
  font-size: 16px;
  font-weight: 600;
}

.layout__user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.layout__username {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  outline: none;
}

.layout__main {
  padding: 0;
  background-color: #f5f7fa;
  overflow-y: auto;
}
</style>
