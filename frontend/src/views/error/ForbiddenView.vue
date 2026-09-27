<script setup lang="ts">
import { useRouter } from 'vue-router'

/**
 * 403 无访问权限页（FR-RBAC-5：前端给出明确提示）。
 *
 * 负责人：a
 */
const router = useRouter()
</script>

<template>
  <div class="wms-page">
    <el-result icon="warning" title="403 无访问权限" sub-title="当前账号未被授予该功能的权限，请联系管理员分配">
      <template #extra>
        <el-button type="primary" @click="router.push('/overview')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>
