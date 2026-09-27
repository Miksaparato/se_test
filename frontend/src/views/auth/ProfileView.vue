<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { changePassword } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'

/**
 * 个人设置页（API-003 / API-004）：查看当前身份与权限、修改本人密码。
 *
 * 负责人：a
 */
const auth = useAuthStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '新密码长度须为 6~64 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

/** 提交改密。 */
async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    form.oldPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
  } catch {
    // 错误提示已由拦截器处理（40104 原密码错误）
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="wms-page">
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" header="当前身份">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="账号">{{ auth.user?.account }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ auth.user?.name ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="角色">{{ auth.roles.join('、') || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="auth.user?.status === 'active' ? 'success' : 'danger'" size="small">
                {{ auth.user?.status === 'active' ? '启用' : '禁用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="最近登录">
              {{ auth.user?.lastLoginAt?.replace('T', ' ') ?? '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" header="我的权限" class="profile__permissions">
          <el-tag v-for="code in auth.permissions" :key="code" size="small" class="profile__tag">
            {{ code }}
          </el-tag>
          <div class="wms-muted">
            权限由管理员在「角色与权限」中配置；权限调整后需重新登录才会生效。
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never" header="修改密码">
          <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="form.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="form.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="loading" @click="handleSubmit">提交</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.profile__permissions {
  margin-top: 16px;
}

.profile__tag {
  margin: 0 6px 6px 0;
}
</style>
