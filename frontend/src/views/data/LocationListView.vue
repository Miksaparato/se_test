<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'

import {
  createLocation,
  deleteLocation,
  getWarehouseLayout,
  listLocations,
  listWarehouses,
  updateLocation,
} from '@/api/data'
import DataTable from '@/components/common/DataTable.vue'
import type { Location, LocationPayload, LocationStatus, Warehouse } from '@/types/data'

/**
 * 库位管理页（A-F2，接口 API-025~028）。
 *
 * 库位是推荐（b）与仿真（c）的核心数据源；本页展示编码/坐标/层/状态/容量，
 * 并对「占用」状态与占用货物的一致性做前端提示（后端亦强制校验）。
 *
 * 负责人：a
 */
const loading = ref(false)
const warehouses = ref<Warehouse[]>([])
const rackOptions = ref<Array<{ id: number; label: string; layerCount: number }>>([])
const rows = ref<Location[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  page_size: 20,
  warehouseId: undefined as number | undefined,
  rackId: undefined as number | undefined,
  status: '' as LocationStatus | '',
})

const dialogVisible = ref(false)
const dialogTitle = ref('新建库位')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<LocationPayload & { occupiedSkuId: number | null }>({
  rackId: undefined,
  code: '',
  x: 0,
  y: 0,
  layer: 1,
  status: 'free',
  capacity: 100,
  occupiedSkuId: null,
})

const rules = {
  code: [{ required: true, message: '请输入库位编码', trigger: 'blur' }],
  x: [{ required: true, message: '请输入平面坐标 x', trigger: 'blur' }],
  y: [{ required: true, message: '请输入平面坐标 y', trigger: 'blur' }],
  layer: [{ required: true, message: '请输入层号', trigger: 'blur' }],
}

/** 当前所选货架的层数上限（用于前端提示，后端仍强制校验）。 */
const layerLimit = computed(
  () => rackOptions.value.find((item) => item.id === form.rackId)?.layerCount ?? undefined,
)

/** 加载仓库与货架选项。 */
async function loadOptions(): Promise<void> {
  const result = await listWarehouses({ page: 1, page_size: 100 })
  warehouses.value = result.list
  if (!query.warehouseId && result.list.length > 0) {
    query.warehouseId = result.list[0]?.id
  }
  await loadRackOptions()
}

/** 根据所选仓库加载货架选项（货架无独立列表接口，取自布局接口）。 */
async function loadRackOptions(): Promise<void> {
  if (!query.warehouseId) {
    rackOptions.value = []
    return
  }
  const layout = await getWarehouseLayout(query.warehouseId)
  rackOptions.value = layout.racks.map((rack) => ({
    id: rack.rackId,
    label: `${rack.code}（${rack.aisle}，${rack.columnCount}×${rack.layerCount}）`,
    layerCount: rack.layerCount,
  }))
}

/** 加载库位列表。 */
async function load(): Promise<void> {
  if (!query.warehouseId) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const result = await listLocations({
      page: query.page,
      page_size: query.page_size,
      warehouse_id: query.warehouseId,
      rack_id: query.rackId,
      status: query.status || undefined,
    })
    rows.value = result.list
    total.value = result.total
  } finally {
    loading.value = false
  }
}

/** 查询。 */
function handleSearch(): void {
  query.page = 1
  void load()
}

/**
 * 仓库切换：刷新货架选项并重置货架过滤。
 *
 * 参数由 el-select 的 @change 传入，业务上只需触发刷新，故不参与计算。
 */
async function handleWarehouseChange(): Promise<void> {
  query.rackId = undefined
  await loadRackOptions()
  void load()
}

/** 打开新建弹窗。 */
function handleCreate(): void {
  editingId.value = null
  dialogTitle.value = '新建库位'
  Object.assign(form, {
    rackId: query.rackId ?? rackOptions.value[0]?.id,
    code: '',
    x: 0,
    y: 0,
    layer: 1,
    status: 'free',
    capacity: 100,
    occupiedSkuId: null,
  })
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗。
 *
 * @param row 行数据
 */
function handleEdit(row: Location): void {
  editingId.value = row.id
  dialogTitle.value = `编辑库位 - ${row.code}`
  Object.assign(form, {
    rackId: row.rackId,
    code: row.code,
    x: row.x,
    y: row.y,
    layer: row.layer,
    status: row.status,
    capacity: row.capacity,
    occupiedSkuId: row.occupiedSkuId,
  })
  dialogVisible.value = true
}

/** 提交表单。 */
async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (!form.rackId) {
    ElMessage.warning('请先选择所属货架')
    return
  }
  if (form.status === 'occupied' && !form.occupiedSkuId) {
    ElMessage.warning('状态为「占用」时必须填写占用货物 id')
    return
  }
  submitting.value = true
  try {
    if (editingId.value == null) {
      await createLocation(form)
      ElMessage.success('库位创建成功')
    } else {
      const { code: _code, rackId: _rackId, ...payload } = form
      await updateLocation(editingId.value, payload)
      ElMessage.success('库位更新成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由拦截器统一处理（42204 编码重复、层号超限等）
  } finally {
    submitting.value = false
  }
}

/**
 * 删除库位。
 *
 * @param row 行数据
 */
async function handleDelete(row: Location): Promise<void> {
  await ElMessageBox.confirm(`确认删除库位「${row.code}」？占用状态无法删除。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteLocation(row.id)
  ElMessage.success('删除成功')
  await load()
}

onMounted(async () => {
  await loadOptions()
  await load()
})
</script>

<template>
  <div class="wms-page">
    <el-card shadow="never">
      <div class="wms-search-bar">
        <span>仓库：</span>
        <el-select
          v-model="query.warehouseId"
          placeholder="请选择仓库"
          style="width: 200px"
          @change="handleWarehouseChange"
        >
          <el-option
            v-for="item in warehouses"
            :key="item.id"
            :label="`${item.name}（${item.code}）`"
            :value="item.id"
          />
        </el-select>
        <span>货架：</span>
        <el-select
          v-model="query.rackId"
          placeholder="全部货架"
          clearable
          style="width: 200px"
          @change="handleSearch"
        >
          <el-option
            v-for="item in rackOptions"
            :key="item.id"
            :label="item.label"
            :value="item.id"
          />
        </el-select>
        <span>状态：</span>
        <el-select
          v-model="query.status"
          placeholder="全部状态"
          clearable
          style="width: 140px"
          @change="handleSearch"
        >
          <el-option label="空闲" value="free" />
          <el-option label="占用" value="occupied" />
          <el-option label="停用" value="disabled" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>

      <div class="wms-toolbar">
        <el-button v-permission="'warehouse:manage'" type="primary" @click="handleCreate">
          新建库位
        </el-button>
        <span class="wms-muted">
          API-027：推荐引擎与仿真引擎只消费「空闲」库位；层号从 1 开始，越小越靠地面。
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
        <el-table-column prop="code" label="库位编码" width="160" />
        <el-table-column label="坐标" width="110">
          <template #default="{ row }">({{ row.x }}, {{ row.y }})</template>
        </el-table-column>
        <el-table-column prop="layer" label="层号" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'free'" type="success" size="small">空闲</el-tag>
            <el-tag v-else-if="row.status === 'occupied'" type="warning" size="small">占用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="capacity" label="容量" width="100" />
        <el-table-column prop="occupiedSkuId" label="占用货物 id" width="120" />
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
        <el-form-item label="所属货架">
          <el-select v-model="form.rackId" :disabled="editingId !== null" style="width: 100%">
            <el-option
              v-for="item in rackOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="库位编码" prop="code">
          <el-input
            v-model="form.code"
            :disabled="editingId !== null"
            placeholder="如 A-01-03-02（全局唯一）"
          />
        </el-form-item>
        <el-form-item label="平面坐标" prop="x">
          <el-input-number v-model="form.x" :min="0" controls-position="right" />
          <span class="dialog__sep">,</span>
          <el-input-number v-model="form.y" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="层号" prop="layer">
          <el-input-number v-model="form.layer" :min="1" controls-position="right" />
          <span v-if="layerLimit" class="wms-muted">该货架层数上限：{{ layerLimit }}</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="空闲" value="free" />
            <el-option label="占用" value="occupied" />
            <el-option label="停用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.status === 'occupied'" label="占用货物 id">
          <el-input-number v-model="form.occupiedSkuId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="容量（体积）">
          <el-input-number v-model="form.capacity" :min="0" controls-position="right" />
        </el-form-item>
        <div class="wms-muted">库位编码与所属货架创建后不可修改。</div>
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
