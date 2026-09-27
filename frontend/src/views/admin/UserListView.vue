<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'

import { createUser, deleteUser, listRoles, listUsers, updateUser, updateUserStatus } from '@/api/admin'
import DataTable from '@/components/common/DataTable.vue'
import { useAuthStore } from '@/stores/auth'
import type { CreateUserPayload, RoleInfo, UpdateUserPayload, UserItem, UserStatus } from '@/types/auth'

/**
 * 用户管理页（A-F3，界面需求 8.6，接口 API-005~009）。
 *
 * 页面需 `user:manage`（路由守卫已拦截）；操作按钮再按同一权限显隐。
 *
 * 负责人：a
 */
const auth = useAuthStore()

const loading = ref(false)
const rows = ref<UserItem[]>([])
const total = ref(0)
const roleOptions = ref<RoleInfo[]>([])
const query = reactive({
  page: 1,
  page_size: 20,
  status: '' as UserStatus | '',
  keyword: '',
})

const dialogVisible = ref(false)
const dialogTitle = ref('新建用户')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<CreateUserPayload & UpdateUserPayload>({
  account: '',
  password: '',
  name: '',
  email: '',
  phone: '',
  status: 'active',
  roles: [],
  remark: '',
})

const rules = {
  account: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_.-]+$/,
      message: '账号只能包含字母、数字、下划线、点与连字符',
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度须为 6~64 个字符', trigger: 'blur' },
  ],
}

/** 加载列表与角色选项。 */
async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listUsers(query)
    rows.value = result.list
    total.value = result.total
    if (roleOptions.value.length === 0) {
      roleOptions.value = await listRoles()
    }
  } finally {
    loading.value = false
  }
}

/** 查询。 */
function handleSearch(): void {
  query.page = 1
  void load()
}

/** 打开新建弹窗。 */
function handleCreate(): void {
  editingId.value = null
  dialogTitle.value = '新建用户'
  Object.assign(form, {
    account: '',
    password: '',
    name: '',
    email: '',
    phone: '',
    status: 'active',
    roles: ['viewer'],
    remark: '',
  })
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗。
 *
 * @param row 行数据
 */
function handleEdit(row: UserItem): void {
  editingId.value = row.id
  dialogTitle.value = `编辑用户 - ${row.account}`
  Object.assign(form, {
    account: row.account,
    password: '',
    name: row.name ?? '',
    email: row.email ?? '',
    phone: row.phone ?? '',
    status: row.status,
    roles: [...row.roles],
    remark: row.remark ?? '',
  })
  dialogVisible.value = true
}

/** 提交表单。 */
async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    if (editingId.value == null) {
      await createUser(form as CreateUserPayload)
      ElMessage.success('用户创建成功')
    } else {
      const { account: _a, password: _p, status: _s, ...payload } = form
      await updateUser(editingId.value, payload)
      ElMessage.success('用户更新成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由拦截器统一处理（42207 账号已存在等）
  } finally {
    submitting.value = false
  }
}

/**
 * 启用/禁用用户。
 *
 * @param row 行数据
 */
async function handleToggleStatus(row: UserItem): Promise<void> {
  const target: UserStatus = row.status === 'active' ? 'disabled' : 'active'
  const action = target === 'active' ? '启用' : '禁用'
  await ElMessageBox.confirm(`确认${action}用户「${row.account}」？`, `${action}确认`, {
    type: 'warning',
    confirmButtonText: action,
    cancelButtonText: '取消',
  })
  await updateUserStatus(row.id, target)
  ElMessage.success(`${action}成功`)
  await load()
}

/**
 * 删除用户。
 *
 * @param row 行数据
 */
async function handleDelete(row: UserItem): Promise<void> {
  await ElMessageBox.confirm(`确认删除用户「${row.account}」？该操作不可恢复。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="wms-page">
    <el-card shadow="never">
      <div class="wms-search-bar">
        <el-input
          v-model="query.keyword"
          placeholder="按账号或姓名搜索"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="query.status"
          placeholder="全部状态"
          clearable
          style="width: 140px"
          @change="handleSearch"
        >
          <el-option label="启用" value="active" />
          <el-option label="禁用" value="disabled" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>

      <div class="wms-toolbar">
        <el-button v-permission="'user:manage'" type="primary" @click="handleCreate">新建用户</el-button>
        <span class="wms-muted">
          API-005~009：禁用后的账号无法登录；内置 admin 账号不可删除，也不能删除/禁用当前登录账号。
        </span>
      </div>

      <DataTable
        v-model:page="query.page"
        v-model:page-size="query.page_size"
        :rows="rows"
        :total="total"
        :loading="loading"
        @update:page="load"
        @update:page-size="load"
      >
        <el-table-column prop="account" label="账号" width="140" />
        <el-table-column prop="name" label="姓名" width="130" />
        <el-table-column label="角色" min-width="150">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles" :key="role" size="small" class="user__role">
              {{ role }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
              {{ row.status === 'active' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="170" />
        <el-table-column label="最近登录" width="170">
          <template #default="{ row }">{{ row.lastLoginAt?.replace('T', ' ') ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'user:manage'" link type="primary" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button
              v-permission="'user:manage'"
              link
              :type="row.status === 'active' ? 'warning' : 'success'"
              :disabled="row.id === auth.user?.id"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'active' ? '禁用' : '启用' }}
            </el-button>
            <el-button
              v-permission="'user:manage'"
              link
              type="danger"
              :disabled="row.id === auth.user?.id"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </DataTable>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px" class="wms-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="账号" prop="account">
          <el-input v-model="form.account" :disabled="editingId !== null" placeholder="登录账号" />
        </el-form-item>
        <el-form-item v-if="editingId === null" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6~64 个字符" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roles" multiple placeholder="请选择角色" style="width: 100%">
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="`${role.name}（${role.code}）`"
              :value="role.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" :disabled="editingId !== null" style="width: 100%">
            <el-option label="启用" value="active" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <div class="wms-muted">
          账号创建后不可修改；密码修改请由用户本人在「个人设置」中完成或重新创建账号。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user__role {
  margin-right: 4px;
}
</style>
