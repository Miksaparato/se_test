<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'

import {
  assignRolePermissions,
  createRole,
  deleteRole,
  listPermissions,
  listRoles,
  updateRole,
} from '@/api/admin'
import type { PermissionInfo, RoleInfo, RolePayload } from '@/types/auth'

/**
 * 角色与权限管理页（A-F3，界面需求 8.6，接口 API-010~015，FR-RBAC-2 / FR-RBAC-4）。
 *
 * 权限分配为**全量覆盖**语义：勾选后提交即替换该角色的全部权限。
 * 内置四角色不可删除，但权限可调整；权限变更后受影响用户需重新登录才生效。
 *
 * 负责人：a
 */
const loading = ref(false)
const roles = ref<RoleInfo[]>([])
const permissions = ref<PermissionInfo[]>([])

const dialogVisible = ref(false)
const dialogTitle = ref('新建角色')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<RolePayload>({ code: '', name: '', description: '' })

const permVisible = ref(false)
const permSubmitting = ref(false)
const permRole = ref<RoleInfo | null>(null)
const selectedPermissions = ref<string[]>([])

const rules = {
  code: [
    { required: true, message: '请输入角色标识', trigger: 'blur' },
    {
      pattern: /^[a-z][a-z0-9_-]{1,31}$/,
      message: '以小写字母开头，仅含小写字母、数字、下划线、连字符',
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
}

/** 加载角色与权限清单。 */
async function load(): Promise<void> {
  loading.value = true
  try {
    const [roleList, permissionList] = await Promise.all([listRoles(), listPermissions()])
    roles.value = roleList
    permissions.value = permissionList
  } finally {
    loading.value = false
  }
}

/** 打开新建弹窗。 */
function handleCreate(): void {
  editingId.value = null
  dialogTitle.value = '新建角色'
  Object.assign(form, { code: '', name: '', description: '' })
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗。
 *
 * @param row 角色
 */
function handleEdit(row: RoleInfo): void {
  editingId.value = row.id
  dialogTitle.value = `编辑角色 - ${row.code}`
  Object.assign(form, { code: row.code, name: row.name, description: row.description ?? '' })
  dialogVisible.value = true
}

/** 提交角色表单。 */
async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    if (editingId.value == null) {
      await createRole(form)
      ElMessage.success('角色创建成功')
    } else {
      const { code: _code, ...payload } = form
      await updateRole(editingId.value, payload)
      ElMessage.success('角色更新成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由拦截器统一处理（42208 角色标识重复等）
  } finally {
    submitting.value = false
  }
}

/**
 * 删除角色。
 *
 * @param row 角色
 */
async function handleDelete(row: RoleInfo): Promise<void> {
  await ElMessageBox.confirm(
    `确认删除角色「${row.name}」？内置角色与仍被用户引用的角色无法删除。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await deleteRole(row.id)
  ElMessage.success('删除成功')
  await load()
}

/**
 * 打开权限分配弹窗。
 *
 * @param row 角色
 */
function handleAssign(row: RoleInfo): void {
  permRole.value = row
  selectedPermissions.value = [...(row.permissions ?? [])]
  permVisible.value = true
}

/** 提交权限分配（全量覆盖）。 */
async function handleAssignSubmit(): Promise<void> {
  if (!permRole.value) {
    return
  }
  permSubmitting.value = true
  try {
    await assignRolePermissions(permRole.value.id, selectedPermissions.value)
    ElMessage.success('权限分配成功；被影响用户需重新登录后生效')
    permVisible.value = false
    await load()
  } catch {
    // 错误提示由拦截器统一处理（42212 非法权限码）
  } finally {
    permSubmitting.value = false
  }
}
</script>

<template>
  <div class="wms-page">
    <el-card shadow="never">
      <div class="wms-toolbar">
        <el-button v-permission="'user:manage'" type="primary" @click="handleCreate">
          新建角色
        </el-button>
        <span class="wms-muted">
          API-010~014：内置四角色（admin/operator/analyst/viewer）不可删除，但权限可调整；
          权限变更后需重新登录才生效（无状态 JWT 的既有取舍）。
        </span>
      </div>

      <el-table v-loading="loading" :data="roles" border stripe size="small">
        <el-table-column prop="code" label="角色标识" width="120" />
        <el-table-column prop="name" label="角色名称" width="140" />
        <el-table-column label="内置" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isBuiltin" type="warning" size="small">内置</el-tag>
            <span v-else class="wms-muted">自定义</span>
          </template>
        </el-table-column>
        <el-table-column prop="userCount" label="用户数" width="90" />
        <el-table-column label="权限" min-width="320">
          <template #default="{ row }">
            <el-tag
              v-for="code in row.permissions ?? []"
              :key="code"
              size="small"
              class="role__perm"
            >
              {{ code }}
            </el-tag>
            <span v-if="!row.permissions || row.permissions.length === 0" class="wms-muted">
              未分配权限
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'user:manage'" link type="primary" @click="handleAssign(row)">
              分配权限
            </el-button>
            <el-button v-permission="'user:manage'" link type="primary" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button
              v-permission="'user:manage'"
              link
              type="danger"
              :disabled="row.isBuiltin"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="520px" class="wms-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="角色标识" prop="code">
          <el-input
            v-model="form.code"
            :disabled="editingId !== null"
            placeholder="小写字母开头，如 auditor"
          />
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="如 审计员" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <div class="wms-muted">角色标识创建后不可修改（权限矩阵与前端路由以其为判断依据）。</div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="permVisible" :title="`分配权限 - ${permRole?.name ?? ''}`" width="600px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="提交后为该角色的全部权限（全量覆盖）"
        description="取消勾选即收回对应权限；权限码清单由《接口文档》1.4 冻结，不可自行新增。"
      />
      <el-checkbox-group v-model="selectedPermissions" class="perm__group">
        <div v-for="item in permissions" :key="item.code" class="perm__item">
          <el-checkbox :label="item.code">
            <span class="perm__name">{{ item.name }}</span>
            <el-tag size="small" type="info" class="perm__type">
              {{ item.type === 'menu' ? '菜单' : '操作' }}
            </el-tag>
            <span class="wms-muted">{{ item.code }}</span>
          </el-checkbox>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSubmitting" @click="handleAssignSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.role__perm {
  margin: 0 4px 4px 0;
}

.perm__group {
  display: block;
  margin-top: 16px;
}

.perm__item {
  padding: 6px 0;
  border-bottom: 1px dashed #ebeef5;
}

.perm__name {
  margin-right: 8px;
}

.perm__type {
  margin-right: 8px;
}
</style>
