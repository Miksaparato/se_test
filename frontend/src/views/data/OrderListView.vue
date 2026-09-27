<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type UploadFile } from 'element-plus'

import {
  createOrder,
  deleteOrder,
  exportOrders,
  importOrders,
  listOrders,
  listSkus,
  updateOrder,
} from '@/api/data'
import DataTable from '@/components/common/DataTable.vue'
import type { ImportResult, Order, OrderPayload, OrderStatus, Sku } from '@/types/data'

/**
 * 订单管理页（A-F2，接口 API-033~036、038、040）。
 *
 * 列表默认按「优先级降序 + 下达时间升序」排序——与出库仿真（c 的 C-B4）的拣选顺序一致。
 *
 * 负责人：a
 */
const loading = ref(false)
const rows = ref<Order[]>([])
const total = ref(0)
const skuOptions = ref<Sku[]>([])
const query = reactive({
  page: 1,
  page_size: 20,
  status: '' as OrderStatus | '',
  orderNo: '',
})

const dialogVisible = ref(false)
const dialogTitle = ref('新建订单')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const importVisible = ref(false)
const importing = ref(false)
const importResult = ref<ImportResult | null>(null)
const importStrategy = ref<'skip' | 'update'>('skip')
const selectedFile = ref<File | null>(null)

const form = reactive<OrderPayload>({
  orderNo: '',
  skuId: undefined,
  quantity: 1,
  priority: 3,
  placedAt: '',
  status: 'pending',
  remark: '',
})

const rules = {
  skuId: [{ required: true, message: '请选择货物', trigger: 'change' }],
  quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }],
  priority: [{ required: true, message: '请输入优先级（1~5）', trigger: 'blur' }],
}

/** 状态展示映射。 */
const statusMeta: Record<OrderStatus, { label: string; type: 'success' | 'warning' | 'info' | 'danger' }> =
  {
    pending: { label: '待出库', type: 'warning' },
    picking: { label: '拣选中', type: 'info' },
    completed: { label: '已完成', type: 'success' },
    cancelled: { label: '已取消', type: 'danger' },
  }

/** 已完成订单不可修改货物与数量（与后端规则一致）。 */
const editingCompleted = computed(() => {
  if (editingId.value == null) {
    return false
  }
  return rows.value.find((row) => row.id === editingId.value)?.status === 'completed'
})

/** 加载货物下拉。 */
async function loadSkuOptions(): Promise<void> {
  const result = await listSkus({ page: 1, page_size: 100 })
  skuOptions.value = result.list
}

/** 加载订单列表。 */
async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listOrders({
      page: query.page,
      page_size: query.page_size,
      status: query.status || undefined,
      order_no: query.orderNo || undefined,
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

/** 打开新建弹窗。 */
function handleCreate(): void {
  editingId.value = null
  dialogTitle.value = '新建订单'
  Object.assign(form, {
    orderNo: '',
    skuId: skuOptions.value[0]?.id,
    quantity: 1,
    priority: 3,
    placedAt: '',
    status: 'pending',
    remark: '',
  })
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗。
 *
 * @param row 行数据
 */
function handleEdit(row: Order): void {
  editingId.value = row.id
  dialogTitle.value = `编辑订单 - ${row.orderNo}`
  Object.assign(form, {
    orderNo: row.orderNo,
    skuId: row.skuId,
    quantity: row.quantity,
    priority: row.priority,
    placedAt: row.placedAt ?? '',
    status: row.status,
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
      const payload: OrderPayload = { ...form }
      if (!payload.orderNo) {
        delete payload.orderNo
      }
      if (!payload.placedAt) {
        delete payload.placedAt
      }
      await createOrder(payload)
      ElMessage.success('订单创建成功')
    } else {
      const { orderNo: _no, ...payload } = form
      await updateOrder(editingId.value, payload)
      ElMessage.success('订单更新成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由拦截器统一处理（42209 订单号重复等）
  } finally {
    submitting.value = false
  }
}

/**
 * 删除订单。
 *
 * @param row 行数据
 */
async function handleDelete(row: Order): Promise<void> {
  await ElMessageBox.confirm(
    `确认删除订单「${row.orderNo}」？仅待出库/已取消状态可删除。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await deleteOrder(row.id)
  ElMessage.success('删除成功')
  await load()
}

/** 打开导入弹窗。 */
function handleImportOpen(): void {
  importResult.value = null
  selectedFile.value = null
  importStrategy.value = 'skip'
  importVisible.value = true
}

/**
 * 记录选择的文件。
 *
 * @param file 上传文件
 */
function handleFileChange(file: UploadFile): void {
  selectedFile.value = (file.raw as File) ?? null
}

/** 执行导入。 */
async function handleImport(): Promise<void> {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择 CSV 或 Excel 文件')
    return
  }
  importing.value = true
  try {
    importResult.value = await importOrders(selectedFile.value, importStrategy.value)
    const result = importResult.value
    ElMessage.success(
      `导入完成：新增 ${result.inserted}、更新 ${result.updated}、跳过 ${result.skipped}、失败 ${result.failed}`,
    )
    await load()
  } catch {
    // 错误提示由拦截器统一处理
  } finally {
    importing.value = false
  }
}

/** 导出。 */
async function handleExport(format: 'csv' | 'json'): Promise<void> {
  await exportOrders(format)
  ElMessage.success(`已导出 ${format.toUpperCase()} 文件`)
}

onMounted(async () => {
  await loadSkuOptions()
  await load()
})
</script>

<template>
  <div class="wms-page">
    <el-card shadow="never">
      <div class="wms-search-bar">
        <el-input
          v-model="query.orderNo"
          placeholder="按订单号搜索"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-select
          v-model="query.status"
          placeholder="全部状态"
          clearable
          style="width: 150px"
          @change="handleSearch"
        >
          <el-option label="待出库" value="pending" />
          <el-option label="拣选中" value="picking" />
          <el-option label="已完成" value="completed" />
          <el-option label="已取消" value="cancelled" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>

      <div class="wms-toolbar">
        <el-button v-permission="'sku:manage'" type="primary" @click="handleCreate">新建订单</el-button>
        <el-button v-permission="'sku:manage'" @click="handleImportOpen">批量导入</el-button>
        <span class="wms-toolbar--right" />
        <el-dropdown>
          <el-button>
            导出
            <el-icon><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleExport('csv')">导出 CSV（Excel 兼容）</el-dropdown-item>
              <el-dropdown-item @click="handleExport('json')">导出 JSON</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <el-alert
        type="info"
        :closable="false"
        class="order__hint"
        title="列表按「优先级降序 + 下达时间升序」排列，与出库仿真的拣选顺序一致（FR-4.1）"
      />

      <DataTable
        v-model:page="query.page"
        v-model:page-size="query.page_size"
        :rows="rows"
        :total="total"
        :loading="loading"
        @update:page="load"
        @update:page-size="load"
      >
        <el-table-column prop="orderNo" label="订单号" width="190" />
        <el-table-column prop="skuCode" label="货物编码" width="140" />
        <el-table-column prop="quantity" label="数量" width="90" />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column label="下达时间" width="180">
          <template #default="{ row }">{{ row.placedAt?.replace('T', ' ') ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusMeta[row.status as OrderStatus].type" size="small">
              {{ statusMeta[row.status as OrderStatus].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'sku:manage'" link type="primary" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button v-permission="'sku:manage'" link type="danger" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </DataTable>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" class="wms-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="订单号">
          <el-input
            v-model="form.orderNo"
            :disabled="editingId !== null"
            placeholder="留空自动生成 SO-yyyyMMdd-序号"
          />
        </el-form-item>
        <el-form-item label="货物" prop="skuId">
          <el-select
            v-model="form.skuId"
            filterable
            placeholder="请选择货物"
            style="width: 100%"
            :disabled="editingCompleted"
          >
            <el-option
              v-for="item in skuOptions"
              :key="item.id"
              :label="`${item.skuCode} - ${item.name}`"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <el-input-number
            v-model="form.quantity"
            :min="1"
            controls-position="right"
            :disabled="editingCompleted"
          />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="form.priority" :min="1" :max="5" controls-position="right" />
          <span class="wms-muted">1~5，越大越先出库</span>
        </el-form-item>
        <el-form-item label="下达时间">
          <el-date-picker
            v-model="form.placedAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="留空取当前时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="待出库" value="pending" />
            <el-option label="拣选中" value="picking" />
            <el-option label="已完成" value="completed" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-alert
          v-if="editingCompleted"
          type="warning"
          :closable="false"
          title="已完成的订单不允许修改货物与数量"
        />
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="批量导入订单" width="640px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="支持 CSV / Excel(.xlsx/.xls)"
        description="表头：orderNo,skuCode,quantity,priority,placedAt,status,remark。注意用 skuCode（而非 id）指定货物；时间支持 yyyy-MM-dd 或 yyyy-MM-ddTHH:mm:ss。"
      />
      <div class="import__body">
        <el-upload
          :auto-upload="false"
          :limit="1"
          accept=".csv,.xlsx,.xls"
          :on-change="handleFileChange"
        >
          <el-button type="primary">选择文件</el-button>
        </el-upload>
        <div class="import__strategy">
          <span>重复订单号：</span>
          <el-radio-group v-model="importStrategy">
            <el-radio label="skip">跳过</el-radio>
            <el-radio label="update">覆盖更新</el-radio>
          </el-radio-group>
        </div>
      </div>

      <template v-if="importResult">
        <el-divider>导入结果</el-divider>
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="总行数">{{ importResult.total }}</el-descriptions-item>
          <el-descriptions-item label="新增">{{ importResult.inserted }}</el-descriptions-item>
          <el-descriptions-item label="更新">{{ importResult.updated }}</el-descriptions-item>
          <el-descriptions-item label="失败">{{ importResult.failed }}</el-descriptions-item>
        </el-descriptions>
        <el-table
          v-if="importResult.errors.length > 0"
          :data="importResult.errors"
          size="small"
          border
          class="import__errors"
        >
          <el-table-column prop="line" label="行号" width="80" />
          <el-table-column prop="column" label="列" width="130" />
          <el-table-column prop="message" label="原因" min-width="220" />
        </el-table>
      </template>

      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="handleImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.order__hint {
  margin-bottom: 12px;
}

.import__body {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.import__strategy {
  display: flex;
  align-items: center;
  gap: 8px;
}

.import__errors {
  margin-top: 12px;
}
</style>
