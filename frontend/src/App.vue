<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="brand">智能仓储库位分配仿真系统</div>
      <nav class="nav">
        <RouterLink to="/recommend" active-class="active">入库推荐</RouterLink>
        <RouterLink to="/compare" active-class="active">方案对比</RouterLink>
        <RouterLink to="/config" active-class="active">权重与规则配置</RouterLink>
      </nav>
    </header>
    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: #1f2d3d;
  color: #fff;
}

.brand {
  font-size: 16px;
  font-weight: 600;
}

.nav {
  display: flex;
  gap: 8px;
}

.nav a {
  color: #c0c4cc;
  text-decoration: none;
  padding: 6px 14px;
  border-radius: 4px;
  font-size: 14px;
}

.nav a:hover {
  color: #fff;
}

.nav a.active {
  color: #fff;
  background: #409eff;
}

.app-main {
  flex: 1;
  padding: 20px 24px;
}
</style>
