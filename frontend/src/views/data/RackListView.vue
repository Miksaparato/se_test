<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'

import { createRack, deleteRack, listWarehouses, updateRack } from '@/api/data'
import DataTable from '@/components/common/DataTable.vue'
import type { Rack, RackPayload, Warehouse } from '@/types/data'

/**
 * 货架管理页（A-F2，接口 API-021~024）。
 *
 * 货架列表取自所选仓库的平面布局接口（API-021），因为货架没有独立列表接口；
 * 新建时可选「按列×层批量生成库位」，编码规则由后端统一生成（a 冻结）：
 * `巷道-货架序号-列-层`，如 `A-01-03-02`。
 *
 * 负责人：a
 */
const loading = ref(false)
const warehouses = ref<Warehouse[]>([])
const rows = ref<Rack[]>([])
const total = ref(0)
const query = reactive({ page: 1, page_size: 20, warehouseId: undefined as number | undefined })

const dialogVisible = ref(false)
const dialogTitle = ref('新建货架')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<RackPayload>({
  warehouseId: undefined,
  code: '',
  aisle: '',
  columnCount: 1,
  layerCount: 1,
  x: 0,
  y: 0,
  orientation: 'row',
  generateLocations: true,
})

const rules = {
  code: [
    { required: true, message: '请输入货架编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]+$/,
      message: '编码只能包含字母、数字、下划线、连字符',
      trigger: 'blur',
    },
  ],
  aisle: [{ required: true, message: '请输入巷道', trigger: 'blur' }],
  columnCount: [{ required: true, message: '请输入列数', trigger: 'blur' }],
  layerCount: [{ required: true, message: '请输入层数', trigger: 'blur' }],
}

/** 预计生成的库位数。 */
const plannedLocationCount = computed(
  () => (form.columnCount ?? 0) * (form.layerCount ?? 0),
)

/**
 * 读取某仓库的货架列表（来自 API-021 布局接口）。
 *
 * @param warehouseId 仓库 id
 */
async function fetchRacks(warehouseId: number): Promise<Rack[]> {
  const { getWarehouseLayout } = await import('@/api/data')
  const layout = await getWarehouseLayout(warehouseId)
  return layout.racks.map((rack) => ({
    id: rack.rackId,
    warehouseId: layout.warehouseId,
    code: rack.code,
    aisle: rack.aisle,
    columnCount: rack.columnCount,
    layerCount: rack.layerCount,
    x: rack.x,
    y: rack.y,
    orientation: rack.orientation,
    locationCount: rack.locations.length,
    createdAt: null,
  }))
}

/** 加载仓库下拉与货架列表。 */
async function load(): Promise<void> {
  loading.value = true
  try {
    if (warehouses.value.length === 0) {
      const result = await listWarehouses({ page: 1, page_size: 100 })
      warehouses.value = result.list
      if (!query.warehouseId && result.list.length > 0) {
        query.warehouseId = result.list[0]?.id
      }
    }
    if (!query.warehouseId) {
      rows.value = []
      total.value = 0
      return
    }
    const list = await fetchRacks(query.warehouseId)
    rows.value = list
    total.value = list.length
  } finally {
    loading.value = false
  }
}

/** 打开新建弹窗。 */
function handleCreate(): void {
  editingId.value = null
  dialogTitle.value = '新建货架'
  Object.assign(form, {
    warehouseId: query.warehouseId,
    code: '',
    aisle: '',
    columnCount: 1,
    layerCount: 1,
    x: 0,
    y: 0,
    orientation: 'row',
    generateLocations: true,
  })
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗。
 *
 * @param row 行数据
 */
function handleEdit(row: Rack): void {
  editingId.value = row.id
  dialogTitle.value = `编辑货架 - ${row.code}`
  Object.assign(form, {
    warehouseId: row.warehouseId,
    code: row.code,
    aisle: row.aisle,
    columnCount: row.columnCount,
    layerCount: row.layerCount,
    x: row.x ?? 0,
    y: row.y ?? 0,
    orientation: row.orientation,
    generateLocations: false,
  })
  dialogVisible.value = true
}

/** 提交表单。 */
async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (!form.warehouseId) {
    ElMessage.warning('请先选择仓库')
    return
  }
  submitting.value = true
  try {
    if (editingId.value == null) {
      const created = await createRack(form)
      ElMessage.success(
        form.generateLocations
          ? `货架创建成功，已生成 ${created.locationCount} 个库位`
          : '货架创建成功',
      )
    } else {
      const { code: _code, aisle: _aisle, warehouseId: _warehouseId, generateLocations: _g, ...payload } = form
      await updateRack(editingId.value, payload)
      ElMessage.success('货架更新成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由拦截器统一处理
  } finally {
    submitting.value = false
  }
}

/**
 * 删除货架。
 *
 * @param row 行数据
 */
async function handleDelete(row: Rack): Promise<void> {
  await ElMessageBox.confirm(
    `确认删除货架「${row.code}」？其下仍有库位时将无法删除。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await deleteRack(row.id)
  ElMessage.success('删除成功')
  await load()
}

onMounted(load)
</script>

<template>
  <div class="wms-page">
    <el-card shadow="never">
      <div class="wms-search-bar">
        <span>仓库：</span>
        <el-select
          v-model="query.warehouseId"
          placeholder="请选择仓库"
          style="width: 240px"
          @change="load"
        >
          <el-option
            v-for="item in warehouses"
            :key="item.id"
            :label="`${item.name}（${item.code}）`"
            :value="item.id"
          />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>

      <div class="wms-toolbar">
        <el-button v-permission="'warehouse:manage'" type="primary" @click="handleCreate">
          新建货架
        </el-button>
        <span class="wms-muted">
          API-022：创建货架时可勾选「批量生成库位」，库位编码按 巷道-货架序号-列-层 自动生成。
        </span>
      </div>

      <DataTable
        v-model:page="query.page"
        v-model:page-size="query.page_size"
        :rows="rows"
        :total="total"
        :loading="loading"
        empty-text="该仓库下暂无货架"
      >
        <el-table-column prop="code" label="货架编码" width="120" />
        <el-table-column prop="aisle" label="巷道" width="80" />
        <el-table-column label="列 × 层" width="110">
          <template #default="{ row }">{{ row.columnCount }} × {{ row.layerCount }}</template>
        </el-table-column>
        <el-table-column label="基准坐标" width="120">
          <template #default="{ row }">({{ row.x ?? 0 }}, {{ row.y ?? 0 }})</template>
        </el-table-column>
        <el-table-column label="排布方向" width="110">
          <template #default="{ row }">
            <el-tag size="small" type="info">
              {{ row.orientation === 'row' ? '沿 x 递增' : '沿 y 递增' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="locationCount" label="库位数" width="90" />
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
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="所属仓库">
          <el-select v-model="form.warehouseId" disabled style="width: 100%">
            <el-option
              v-for="item in warehouses"
              :key="item.id"
              :label="`${item.name}（${item.code}）`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="货架编码" prop="code">
          <el-input v-model="form.code" :disabled="editingId !== null" placeholder="如 A-01" />
        </el-form-item>
        <el-form-item label="巷道" prop="aisle">
          <el-input v-model="form.aisle" :disabled="editingId !== null" placeholder="如 A" />
        </el-form-item>
        <el-form-item label="列数" prop="columnCount">
          <el-input-number v-model="form.columnCount" :min="1" :max="200" controls-position="right" />
        </el-form-item>
        <el-form-item label="层数" prop="layerCount">
          <el-input-number v-model="form.layerCount" :min="1" :max="50" controls-position="right" />
        </el-form-item>
        <el-form-item label="基准坐标">
          <el-input-number v-model="form.x" :min="0" controls-position="right" />
          <span class="dialog__sep">,</span>
          <el-input-number v-model="form.y" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="库位排布方向">
          <el-radio-group v-model="form.orientation">
            <el-radio label="row">沿 x 递增</el-radio>
            <el-radio label="column">沿 y 递增</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="editingId === null" label="批量生成库位">
          <el-checkbox v-model="form.generateLocations">
            按列×层生成 {{ plannedLocationCount }} 个库位
          </el-checkbox>
        </el-form-item>
        <div class="wms-muted">货架编码与巷道创建后不可修改（已生成库位的编码依赖它们）。</div>
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
