<script setup lang="ts">
/**
 * 404 页面不存在。
 *
 * 负责人：a
 */
</script>

<template>
  <div class="wms-page">
    <el-result icon="info" title="404 页面不存在" sub-title="请检查地址是否正确">
      <template #extra>
        <el-button type="primary" @click="$router.push('/overview')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>
