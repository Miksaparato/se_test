<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { comparePlans, exportReport, listPlans } from '@/api/compare'
import { listWarehouses } from '@/api/data'
import SeriesChart from '@/components/charts/SeriesChart.vue'
import type { CompareResult, Plan, Warehouse } from '@/types'

/**
 * 结果对比页（界面需求 8.5 / B-F3 / FR-5.1、FR-5.2）。
 *
 * 指标表格 + 柱状/折线图 + 优化建议文字 + 报告导出；
 * 图表统一走公共组件 {@link SeriesChart}（《代码规范》5.6：禁止在页面内散写 option）。
 *
 * 负责人：b
 */
const warehouses = ref<Warehouse[]>([])
const plans = ref<Plan[]>([])
const selectedIds = ref<number[]>([])
const loading = ref(false)
const result = ref<CompareResult | null>(null)

const query = reactive({ warehouseId: undefined as number | undefined })

/** 图表类目：本次对比的方案名。 */
const categories = computed(() => result.value?.metrics.map((item) => item.name) ?? [])

/** 图表系列：总路程（柱）+ 高频货平均距离（折线，右轴）。 */
const series = computed(() => {
  const metrics = result.value?.metrics ?? []
  return [
    { name: '总搬运路程', type: 'bar' as const, data: metrics.map((item) => item.totalDistance) },
    {
      name: '高频货平均距离',
      type: 'line' as const,
      axis: 1,
      data: metrics.map((item) => item.hotAvgDistance),
      color: '#f56c6c',
    },
  ]
})

/** 加载仓库与方案列表（数据由 c 的仿真产生）。 */
async function load(): Promise<void> {
  const warehousePage = await listWarehouses({ page: 1, page_size: 100 })
  warehouses.value = warehousePage.list
  if (!query.warehouseId) {
    query.warehouseId = warehousePage.list[0]?.id
  }
  await loadPlans()
}

/** 加载方案列表。 */
async function loadPlans(): Promise<void> {
  plans.value = await listPlans(query.warehouseId)
  selectedIds.value = selectedIds.value.filter((id) => plans.value.some((plan) => plan.id === id))
}

/** 仓库切换。 */
async function onWarehouseChange(): Promise<void> {
  result.value = null
  await loadPlans()
}

/**
 * 勾选/取消勾选方案。
 *
 * @param id 方案 id
 * @param checked 是否选中
 */
function togglePlan(id: number, checked: boolean): void {
  if (checked) {
    if (!selectedIds.value.includes(id)) {
      selectedIds.value.push(id)
    }
  } else {
    selectedIds.value = selectedIds.value.filter((item) => item !== id)
  }
}

/**
 * el-table 复选框变更入口（模板里不写 TS 标注，避免模板编译器按 JS 解析报错）。
 *
 * @param id 方案 id
 * @param value 复选框值
 */
function onTogglePlan(id: number, value: boolean | string | number): void {
  togglePlan(id, Boolean(value))
}

/** 执行多方案对比（API-051）。 */
async function onCompare(): Promise<void> {
  if (selectedIds.value.length < 2) {
    ElMessage.warning('请至少选择两个方案进行对比')
    return
  }
  loading.value = true
  try {
    result.value = await comparePlans({ planIds: selectedIds.value })
  } catch {
    result.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 导出对比报告（API-053）。
 *
 * 报告接口需要 `Authorization: Bearer <token>`，因此不能直接用 `<a href>` 下载，
 * 必须走统一封装的 `download()`（带鉴权头 + blob 保存）。
 *
 * @param id 基线方案 id
 * @param format 导出格式（md / csv）
 */
async function onExport(id: number, format: 'md' | 'csv'): Promise<void> {
  await exportReport(id, format)
}

/**
 * 数值格式化。
 *
 * @param value 数值
 * @param digits 小数位
 */
function fmt(value: number | null | undefined, digits = 1): string {
  return value == null ? '-' : value.toFixed(digits)
}

onMounted(async () => {
  try {
    await load()
  } catch {
    // 错误提示已由 http 拦截器统一处理
  }
})
</script>

<template>
  <div class="wms-page">
    <el-card shadow="never">
      <template #header>分配方案列表（API-049）</template>

      <div class="wms-search-bar">
        <span>仓库：</span>
        <el-select v-model="query.warehouseId" style="width: 220px" @change="onWarehouseChange">
          <el-option
            v-for="item in warehouses"
            :key="item.id"
            :label="`${item.name}（${item.code}）`"
            :value="item.id"
          />
        </el-select>
        <el-button :loading="loading" @click="loadPlans">刷新方案</el-button>
        <el-button type="primary" :loading="loading" @click="onCompare">对比所选方案</el-button>
        <span class="wms-muted">已选 {{ selectedIds.length }} 个方案</span>
      </div>

      <el-table :data="plans" border stripe size="small">
        <el-table-column label="对比" width="70">
          <template #default="{ row }">
            <el-checkbox
              :model-value="selectedIds.includes(row.id)"
              @change="onTogglePlan(row.id, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="id" label="方案 id" width="90" />
        <el-table-column prop="planNo" label="方案编号" width="170" />
        <el-table-column prop="name" label="方案名称" min-width="150" />
        <el-table-column prop="strategyName" label="策略" width="120" />
        <el-table-column label="总搬运路程" width="120">
          <template #default="{ row }">{{ fmt(row.totalDistance) }}</template>
        </el-table-column>
        <el-table-column label="平均路程" width="110">
          <template #default="{ row }">{{ fmt(row.avgDistance) }}</template>
        </el-table-column>
        <el-table-column label="高频货平均距离" width="140">
          <template #default="{ row }">{{ fmt(row.hotAvgDistance) }}</template>
        </el-table-column>
        <el-table-column label="重货违规" width="100">
          <template #default="{ row }">
            <el-tag :type="row.violations > 0 ? 'danger' : 'success'" size="small">
              {{ row.violations }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="订单数" width="90">
          <template #default="{ row }">{{ row.orderCount }}</template>
        </el-table-column>
        <el-table-column label="报告" width="150">
          <template #default="{ row }">
            <el-button
              v-permission="'report:export'"
              link
              type="primary"
              size="small"
              @click="onExport(row.id, 'md')"
            >
              MD
            </el-button>
            <el-button
              v-permission="'report:export'"
              link
              type="primary"
              size="small"
              @click="onExport(row.id, 'csv')"
            >
              CSV
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无方案：请先到「策略仿真」跑一次入库仿真" :image-size="70" />
        </template>
      </el-table>
    </el-card>

    <el-card v-if="result" shadow="never">
      <template #header>对比结果（API-051）</template>

      <el-table :data="result.metrics" border stripe size="small">
        <el-table-column prop="name" label="方案" min-width="150" />
        <el-table-column prop="strategy" label="策略" width="140" />
        <el-table-column label="总搬运路程" width="130">
          <template #default="{ row }">{{ fmt(row.totalDistance) }}</template>
        </el-table-column>
        <el-table-column label="平均路程" width="120">
          <template #default="{ row }">{{ fmt(row.avgDistance) }}</template>
        </el-table-column>
        <el-table-column label="高频货平均距离" width="150">
          <template #default="{ row }">{{ fmt(row.hotAvgDistance) }}</template>
        </el-table-column>
        <el-table-column label="重货违规数" width="120">
          <template #default="{ row }">{{ row.violations }}</template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">指标对比图</el-divider>
      <SeriesChart
        :categories="categories"
        :series="series"
        :axis-names="['总路程', '高频货距离']"
        :height="340"
      />

      <el-divider content-position="left">优化建议（API-052）</el-divider>
      <el-alert :title="result.suggestion" type="success" :closable="false" show-icon />
    </el-card>
  </div>
</template>
