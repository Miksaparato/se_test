<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'

import {
  createWarehouse,
  deleteWarehouse,
  listWarehouses,
  updateWarehouse,
} from '@/api/data'
import DataTable from '@/components/common/DataTable.vue'
import type { Warehouse, WarehousePayload } from '@/types/data'

/**
 * 仓库管理页（A-F2，界面需求 8.2，接口 API-016~020）。
 *
 * 可写操作需 `warehouse:manage`（仅 admin）；只读角色仍可查看（sim:view）。
 *
 * 负责人：a
 */
const loading = ref(false)
const rows = ref<Warehouse[]>([])
const total = ref(0)
const query = reactive({ page: 1, page_size: 20, keyword: '' })

const dialogVisible = ref(false)
const dialogTitle = ref('新建仓库')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<WarehousePayload>({
  code: '',
  name: '',
  length: null,
  width: null,
  height: null,
  exitX: 0,
  exitY: 0,
  remark: '',
})

const rules = {
  code: [
    { required: true, message: '请输入仓库编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]+$/,
      message: '编码只能包含字母、数字、下划线、连字符',
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: '请输入仓库名称', trigger: 'blur' }],
}

/** 加载列表。 */
async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listWarehouses(query)
    rows.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

/** 查询（回到第一页）。 */
function handleSearch(): void {
  query.page = 1
  void load()
}

/** 打开新建弹窗。 */
function handleCreate(): void {
  editingId.value = null
  dialogTitle.value = '新建仓库'
  Object.assign(form, {
    code: '',
    name: '',
    length: null,
    width: null,
    height: null,
    exitX: 0,
    exitY: 0,
    remark: '',
  })
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗。
 *
 * @param row 行数据
 */
function handleEdit(row: Warehouse): void {
  editingId.value = row.id
  dialogTitle.value = `编辑仓库 - ${row.code}`
  Object.assign(form, {
    code: row.code,
    name: row.name,
    length: row.length,
    width: row.width,
    height: row.height,
    exitX: row.exit?.x ?? 0,
    exitY: row.exit?.y ?? 0,
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
      await createWarehouse(form)
      ElMessage.success('仓库创建成功')
    } else {
      // 编码不可修改，提交时不带 code
      const { code: _code, ...payload } = form
      await updateWarehouse(editingId.value, payload)
      ElMessage.success('仓库更新成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由拦截器统一处理（42205 编码重复等）
  } finally {
    submitting.value = false
  }
}

/**
 * 删除仓库。
 *
 * @param row 行数据
 */
async function handleDelete(row: Warehouse): Promise<void> {
  await ElMessageBox.confirm(
    `确认删除仓库「${row.name}」？若其下仍有货架将无法删除。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await deleteWarehouse(row.id)
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
          placeholder="按编码或名称搜索"
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>

      <div class="wms-toolbar">
        <el-button v-permission="'warehouse:manage'" type="primary" @click="handleCreate">
          新建仓库
        </el-button>
        <span class="wms-muted">
          API-016~020：仓库建模（长/宽/高、出库口坐标）；出库口坐标是曼哈顿距离计算的终点。
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
        <el-table-column prop="code" label="仓库编码" width="140" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="尺寸（长×宽×高）" width="160">
          <template #default="{ row }">
            {{ row.length ?? '-' }} × {{ row.width ?? '-' }} × {{ row.height ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="出库口坐标" width="130">
          <template #default="{ row }">({{ row.exit?.x ?? 0 }}, {{ row.exit?.y ?? 0 }})</template>
        </el-table-column>
        <el-table-column prop="rackCount" label="货架数" width="90" />
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'warehouse:manage'" link type="primary" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="'warehouse:manage'" link type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </DataTable>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" class="wms-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="仓库编码" prop="code">
          <el-input v-model="form.code" :disabled="editingId !== null" placeholder="如 WH-01" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如 一号仓" />
        </el-form-item>
        <el-form-item label="长">
          <el-input-number v-model="form.length" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="宽">
          <el-input-number v-model="form.width" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="高 / 最大层数">
          <el-input-number v-model="form.height" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="出库口坐标">
          <el-input-number v-model="form.exitX" :min="0" controls-position="right" />
          <span class="dialog__sep">,</span>
          <el-input-number v-model="form.exitY" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <div class="wms-muted">编码创建后不可修改（库位编码与平面图数据以其为引用基础）。</div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dialog__sep {
  margin: 0 8px;
}
</style>
