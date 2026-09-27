<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

import { useAuthStore } from '@/stores/auth'

/**
 * 登录页（A-F1，界面需求 8.1）。
 *
 * 账号密码登录 → 保存 Token 与用户信息 → 按角色加载菜单（菜单过滤见 MainLayout）。
 *
 * 负责人：a
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const form = reactive({
  account: '',
  password: '',
})

const loading = ref(false)
const formRef = ref()

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

onMounted(() => {
  // 已登录时直接进入系统
  if (auth.isLoggedIn) {
    void router.replace('/overview')
  }
})

/** 提交登录。 */
async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    await auth.login({ account: form.account.trim(), password: form.password })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string | undefined) ?? '/overview'
    await router.replace(redirect)
  } catch {
    // 错误提示已由 http 拦截器统一处理（40102 账号或密码错误）
  } finally {
    loading.value = false
  }
}

/** 填入演示账号，便于验收时快速切换角色。 */
function fill(account: string): void {
  form.account = account
  form.password = 'admin123'
}
</script>

<template>
  <div class="login">
    <el-card class="login__card" shadow="always">
      <div class="login__header">
        <h1 class="login__title">智能仓储库位分配仿真系统</h1>
        <p class="wms-muted">小型教学与辅助决策工具 · RBAC 权限控制</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleSubmit"
      >
        <el-form-item label="账号" prop="account">
          <el-input v-model="form.account" placeholder="请输入账号" clearable size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            size="large"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="login__submit" :loading="loading" @click="handleSubmit">
          登录
        </el-button>
      </el-form>

      <el-divider>演示账号（初始密码 admin123）</el-divider>
      <div class="login__accounts">
        <el-button size="small" @click="fill('admin')">系统管理员</el-button>
        <el-button size="small" @click="fill('operator')">仓库操作员</el-button>
        <el-button size="small" @click="fill('analyst')">分析人员</el-button>
        <el-button size="small" @click="fill('viewer')">只读访客</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3a5f 0%, #409eff 100%);
}

.login__card {
  width: 420px;
  padding: 8px 12px;
}

.login__header {
  text-align: center;
  margin-bottom: 16px;
}

.login__title {
  margin: 0 0 6px;
  font-size: 20px;
  color: #1f3a5f;
}

.login__submit {
  width: 100%;
}

.login__accounts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.login__accounts :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
