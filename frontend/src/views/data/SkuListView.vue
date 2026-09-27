<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type UploadFile } from 'element-plus'

import {
  createSku,
  deleteSku,
  exportSkus,
  importSkus,
  listSkus,
  updateSku,
} from '@/api/data'
import DataTable from '@/components/common/DataTable.vue'
import type { ImportResult, Sku, SkuPayload } from '@/types/data'

/**
 * 货物管理页（A-F2，接口 API-029~032、037、039）。
 *
 * 重量 / 周转频次 / 出库优先级 是推荐引擎（b）与仿真引擎（c）的核心输入，
 * 本页在表单中给出字段口径提示。
 *
 * 负责人：a
 */
const loading = ref(false)
const rows = ref<Sku[]>([])
const total = ref(0)
const query = reactive({ page: 1, page_size: 20, keyword: '', category: '' })

const dialogVisible = ref(false)
const dialogTitle = ref('新建货物')
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const importVisible = ref(false)
const importing = ref(false)
const importResult = ref<ImportResult | null>(null)
const importStrategy = ref<'skip' | 'update'>('skip')
const selectedFile = ref<File | null>(null)

const form = reactive<SkuPayload>({
  skuCode: '',
  name: '',
  weight: 0,
  turnoverRate: 0,
  priority: 3,
  category: '',
  size: { length: null, width: null, height: null },
  remark: '',
})

const rules = {
  skuCode: [
    { required: true, message: '请输入 SKU 编码', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]+$/,
      message: '编码只能包含字母、数字、下划线、连字符',
      trigger: 'blur',
    },
  ],
  name: [{ required: true, message: '请输入货物名称', trigger: 'blur' }],
  weight: [{ required: true, message: '请输入重量', trigger: 'blur' }],
  turnoverRate: [{ required: true, message: '请输入周转频次', trigger: 'blur' }],
  priority: [{ required: true, message: '请输入出库优先级（1~5）', trigger: 'blur' }],
}

/** 加载列表。 */
async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await listSkus(query)
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
  dialogTitle.value = '新建货物'
  Object.assign(form, {
    skuCode: '',
    name: '',
    weight: 0,
    turnoverRate: 0,
    priority: 3,
    category: '',
    size: { length: null, width: null, height: null },
    remark: '',
  })
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗。
 *
 * @param row 行数据
 */
function handleEdit(row: Sku): void {
  editingId.value = row.id
  dialogTitle.value = `编辑货物 - ${row.skuCode}`
  Object.assign(form, {
    skuCode: row.skuCode,
    name: row.name,
    weight: row.weight,
    turnoverRate: row.turnoverRate,
    priority: row.priority,
    category: row.category ?? '',
    size: row.size ?? { length: null, width: null, height: null },
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
      await createSku(form)
      ElMessage.success('货物创建成功')
    } else {
      const { skuCode: _code, ...payload } = form
      await updateSku(editingId.value, payload)
      ElMessage.success('货物更新成功')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由拦截器统一处理（42206 编码重复等）
  } finally {
    submitting.value = false
  }
}

/**
 * 删除货物。
 *
 * @param row 行数据
 */
async function handleDelete(row: Sku): Promise<void> {
  await ElMessageBox.confirm(
    `确认删除货物「${row.name}」？被库位占用或被订单引用时将无法删除。`,
    '删除确认',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
  )
  await deleteSku(row.id)
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
    importResult.value = await importSkus(selectedFile.value, importStrategy.value)
    const result = importResult.value
    if (result.failed > 0) {
      ElMessage.warning(
        `导入完成：新增 ${result.inserted}、更新 ${result.updated}、跳过 ${result.skipped}、失败 ${result.failed}`,
      )
    } else {
      ElMessage.success(
        `导入完成：新增 ${result.inserted}、更新 ${result.updated}、跳过 ${result.skipped}`,
      )
    }
    await load()
  } catch {
    // 错误提示由拦截器统一处理
  } finally {
    importing.value = false
  }
}

/** 导出 CSV。 */
async function handleExport(format: 'csv' | 'json'): Promise<void> {
  await exportSkus(format)
  ElMessage.success(`已导出 ${format.toUpperCase()} 文件`)
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
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="query.category"
          placeholder="按品类搜索"
          clearable
          style="width: 160px"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
      </div>

      <div class="wms-toolbar">
        <el-button v-permission="'sku:manage'" type="primary" @click="handleCreate">新建货物</el-button>
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

      <DataTable
        v-model:page="query.page"
        v-model:page-size="query.page_size"
        :rows="rows"
        :total="total"
        :loading="loading"
        @update:page="load"
        @update:page-size="load"
      >
        <el-table-column prop="skuCode" label="SKU 编码" width="150" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="weight" label="重量(kg)" width="100" />
        <el-table-column prop="turnoverRate" label="周转频次" width="100" />
        <el-table-column prop="priority" label="优先级" width="90" />
        <el-table-column prop="category" label="品类" width="100" />
        <el-table-column label="尺寸(长×宽×高)" width="150">
          <template #default="{ row }">
            <span v-if="row.size">
              {{ row.size.length ?? '-' }} × {{ row.size.width ?? '-' }} × {{ row.size.height ?? '-' }}
            </span>
            <span v-else class="wms-muted">未录入</span>
          </template>
        </el-table-column>
        <el-table-column prop="volume" label="体积" width="90" />
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" class="wms-dialog">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="SKU 编码" prop="skuCode">
          <el-input v-model="form.skuCode" :disabled="editingId !== null" placeholder="如 SKU-001" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="重量(kg)" prop="weight">
          <el-input-number v-model="form.weight" :min="0" :precision="3" controls-position="right" />
          <span class="wms-muted">重货倾向低层（权重默认 30%）</span>
        </el-form-item>
        <el-form-item label="周转频次" prop="turnoverRate">
          <el-input-number v-model="form.turnoverRate" :min="0" :precision="4" controls-position="right" />
          <span class="wms-muted">高频货靠近出库口（权重默认 40%）</span>
        </el-form-item>
        <el-form-item label="出库优先级" prop="priority">
          <el-input-number v-model="form.priority" :min="1" :max="5" controls-position="right" />
          <span class="wms-muted">1~5，越大越紧急（权重默认 20%）</span>
        </el-form-item>
        <el-form-item label="品类">
          <el-input v-model="form.category" placeholder="如 电子" />
        </el-form-item>
        <el-form-item label="尺寸">
          <el-input-number v-model="form.size!.length" :min="0" controls-position="right" placeholder="长" />
          <span class="dialog__sep">×</span>
          <el-input-number v-model="form.size!.width" :min="0" controls-position="right" placeholder="宽" />
          <span class="dialog__sep">×</span>
          <el-input-number v-model="form.size!.height" :min="0" controls-position="right" placeholder="高" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <div class="wms-muted">
          尺寸要么三项都填、要么都不填；用于与库位容量（体积）做校验。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="importVisible" title="批量导入货物" width="640px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="支持 CSV / Excel(.xlsx/.xls)"
        description="表头：skuCode,name,weight,turnoverRate,priority,category,length,width,height,remark（列顺序可变，按列名匹配）。合法行入库，非法行逐行提示行号与原因。"
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
          <span>重复编码处理：</span>
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
        <div v-if="importResult.truncated" class="wms-muted">
          错误明细超过 100 条，仅显示前 100 条。
        </div>
      </template>

      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" @click="handleImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.dialog__sep {
  margin: 0 6px;
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
