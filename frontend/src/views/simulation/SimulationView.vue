<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { listPlans } from '@/api/compare'
import { getWarehouseLayout, listSkus, listWarehouses } from '@/api/data'
import {
  generateOrders,
  listStrategies,
  resetInboundSimulation,
  simulateInbound,
  simulateOutbound,
} from '@/api/simulation'
import SeriesChart from '@/components/charts/SeriesChart.vue'
import WarehouseLayout from '@/components/warehouse/WarehouseLayout.vue'
import type {
  DistanceMetricName,
  GenerateOrdersResult,
  InboundSimulation,
  LayoutLocation,
  OutboundOrderResult,
  OutboundSimulation,
  Plan,
  Sku,
  StrategyInfo,
  Warehouse,
  WarehouseLayout as WarehouseLayoutData,
} from '@/types'

/**
 * 入库策略仿真 + 出库仿真与统计（界面需求 8.4 / C-F3、C-F4、C-F5）。
 *
 * 两个页签对应两条业务链路：
 * 1. 入库仿真：选策略 → 逐项选位并记录理由 → 产出「库位分配方案」（API-054~057）；
 * 2. 出库仿真：生成订单集 → 按方案拣选 → 路程统计与拣选路径（API-058~062）。
 *
 * 平面图与图表分别复用 c 的公共组件 {@link WarehouseLayout}（C-F1）与
 * {@link SeriesChart}（《代码规范》5.6），本页不写任何 canvas/option 代码。
 *
 * 负责人：c
 */

/** 入库明细行（勾选 + 数量）。 */
interface InboundRow {
  skuId: number
  skuCode: string
  name: string
  weight: number
  turnoverRate: number
  priority: number
  category: string | null
  quantity: number
  checked: boolean
}

const activeTab = ref('inbound')

// ---------------------------------------------------------------- 公共：仓库 / 货物 / 平面图
const warehouses = ref<Warehouse[]>([])
const skus = ref<Sku[]>([])
const layout = ref<WarehouseLayoutData | null>(null)
const warehouseId = ref<number>()

const flatLocations = computed<LayoutLocation[]>(
  () => (layout.value?.racks ?? []).flatMap((rack) => rack.locations),
)
const exitPoint = computed(() => layout.value?.exit ?? { x: 0, y: 0 })

// ---------------------------------------------------------------- 入库仿真（C-F3）
const strategies = ref<StrategyInfo[]>([])
const strategy = ref('smart')
const distanceMetric = ref<DistanceMetricName>('manhattan')
const layerHeight = ref(1)
const rows = ref<InboundRow[]>([])
const inboundLoading = ref(false)
const inboundResult = ref<InboundSimulation | null>(null)
const selectedAssignment = ref<number | null>(null)

const currentStrategy = computed(() =>
  strategies.value.find((item) => item.name === strategy.value),
)
const checkedRows = computed(() => rows.value.filter((row) => row.checked))
const highlightIds = computed(
  () => inboundResult.value?.assignments.map((item) => item.locationId) ?? [],
)

// ---------------------------------------------------------------- 出库仿真（C-F4 / C-F5）
const plans = ref<Plan[]>([])
const selectedPlanId = ref<number | null>(null)
const speed = ref(1.5)
const orderForm = reactive({
  count: 30,
  priorityMin: 1,
  priorityMax: 5,
  quantityMin: 1,
  quantityMax: 20,
  randomSeed: 20260910 as number | null,
})
const generated = ref<GenerateOrdersResult | null>(null)
const generating = ref(false)
const outboundLoading = ref(false)
const outboundResult = ref<OutboundSimulation | null>(null)
const selectedOrderId = ref<number | null>(null)

const selectedOrder = computed<OutboundOrderResult | null>(
  () =>
    outboundResult.value?.orders.find((item) => item.orderId === selectedOrderId.value) ?? null,
)
/** 所选订单的拣选路径坐标（API-061 的 paths 按订单分开返回）。 */
const pathPoints = computed(
  () =>
    outboundResult.value?.paths.find((item) => item.orderId === selectedOrderId.value)?.points ??
    [],
)
const pathHighlight = computed(
  () => selectedOrder.value?.picks.map((item) => item.locationId) ?? [],
)

/** 各订单路程图表数据（按订单号类目，超出 20 单时只展示前 20 单，避免 x 轴糊成一团）。 */
const chartCategories = computed(
  () => outboundResult.value?.orders.slice(0, 20).map((item) => item.orderNo) ?? [],
)
const chartSeries = computed(() => {
  const orders = outboundResult.value?.orders.slice(0, 20) ?? []
  return [
    { name: '单订单路程', type: 'bar' as const, data: orders.map((item) => item.distance) },
    {
      name: '平均路程',
      type: 'line' as const,
      data: orders.map(() => outboundResult.value?.stats.avgDistance ?? 0),
      color: '#f56c6c',
    },
  ]
})

// ---------------------------------------------------------------- 数据加载
async function loadWarehouses(): Promise<void> {
  const result = await listWarehouses({ page: 1, page_size: 100 })
  warehouses.value = result.list
  warehouseId.value = result.list[0]?.id
}

async function loadSkus(): Promise<void> {
  const result = await listSkus({ page: 1, page_size: 100 })
  skus.value = result.list
  rows.value = result.list.map((sku) => ({
    skuId: sku.id,
    skuCode: sku.skuCode,
    name: sku.name,
    weight: sku.weight,
    turnoverRate: sku.turnoverRate,
    priority: sku.priority,
    category: sku.category,
    quantity: 10,
    checked: false,
  }))
}

async function loadStrategies(): Promise<void> {
  strategies.value = await listStrategies()
}

async function loadLayout(): Promise<void> {
  if (!warehouseId.value) {
    layout.value = null
    return
  }
  layout.value = await getWarehouseLayout(warehouseId.value)
}

async function loadPlans(): Promise<void> {
  if (!warehouseId.value) {
    return
  }
  plans.value = await listPlans(warehouseId.value)
  if (plans.value.length > 0 && selectedPlanId.value == null) {
    selectedPlanId.value = plans.value[0]?.id ?? null
  }
}

/** 仓库切换后刷新布局与方案列表。 */
async function onWarehouseChange(): Promise<void> {
  inboundResult.value = null
  outboundResult.value = null
  selectedOrderId.value = null
  await Promise.all([loadLayout(), loadPlans()])
}

onMounted(async () => {
  try {
    await Promise.all([loadWarehouses(), loadSkus(), loadStrategies()])
    await onWarehouseChange()
  } catch {
    // 错误提示已由 http 拦截器统一处理
  }
})

// ---------------------------------------------------------------- 入库仿真动作
/** 全选/全不选。 */
function toggleAll(checked: boolean): void {
  rows.value.forEach((row) => (row.checked = checked))
}

/** 执行入库仿真（API-055）。 */
async function runInbound(): Promise<void> {
  if (!warehouseId.value) {
    ElMessage.warning('请先选择仓库')
    return
  }
  if (checkedRows.value.length === 0) {
    ElMessage.warning('请至少勾选一项入库明细')
    return
  }
  inboundLoading.value = true
  try {
    inboundResult.value = await simulateInbound({
      warehouseId: warehouseId.value,
      strategy: strategy.value,
      distanceMetric: distanceMetric.value,
      layerHeight: layerHeight.value,
      items: checkedRows.value.map((row) => ({ skuId: row.skuId, quantity: row.quantity })),
    })
    selectedAssignment.value = inboundResult.value.assignments[0]?.locationId ?? null
    await loadLayout()
    await loadPlans()
    ElMessage.success(
      `仿真完成：${inboundResult.value.simulationId}，方案 #${inboundResult.value.planId}`,
    )
  } catch {
    // 错误提示已由 http 拦截器统一处理
  } finally {
    inboundLoading.value = false
  }
}

/** 清空重置（API-057）：丢弃本次仿真并删除其产出的方案。 */
async function resetInbound(): Promise<void> {
  if (!inboundResult.value) {
    return
  }
  await ElMessageBox.confirm(
    `将清除仿真 ${inboundResult.value.simulationId} 及其方案 #${inboundResult.value.planId}，基础数据不受影响。确认继续？`,
    '清空重置',
    { type: 'warning', confirmButtonText: '重置', cancelButtonText: '取消' },
  )
  await resetInboundSimulation(inboundResult.value.simulationId)
  inboundResult.value = null
  selectedAssignment.value = null
  await loadPlans()
  ElMessage.success('已清空重置，可换策略重新仿真')
}

// ---------------------------------------------------------------- 出库仿真动作
/** 生成随机测试订单集（API-062）。 */
async function runGenerate(): Promise<void> {
  if (!warehouseId.value) {
    ElMessage.warning('请先选择仓库')
    return
  }
  generating.value = true
  try {
    generated.value = await generateOrders({
      count: orderForm.count,
      skuIds: skus.value.map((sku) => sku.id),
      priorityRange: [orderForm.priorityMin, orderForm.priorityMax],
      quantityRange: [orderForm.quantityMin, orderForm.quantityMax],
      randomSeed: orderForm.randomSeed ?? undefined,
    })
    ElMessage.success(
      `已生成 ${generated.value.generated} 张待出库订单（种子 ${generated.value.randomSeed}）`,
    )
  } catch {
    // 错误提示已由 http 拦截器统一处理
  } finally {
    generating.value = false
  }
}

/** 执行出库仿真（API-058）：按所选方案的占用映射拣选。 */
async function runOutbound(): Promise<void> {
  if (!warehouseId.value) {
    ElMessage.warning('请先选择仓库')
    return
  }
  outboundLoading.value = true
  try {
    outboundResult.value = await simulateOutbound({
      planId: selectedPlanId.value,
      warehouseId: selectedPlanId.value == null ? warehouseId.value : null,
      pickingMode: 'single',
      speed: speed.value,
      distanceMetric: distanceMetric.value,
    })
    selectedOrderId.value = outboundResult.value.orders[0]?.orderId ?? null
    ElMessage.success(
      `出库仿真完成：${outboundResult.value.simulationId}，共 ${outboundResult.value.stats.orderCount} 单`,
    )
  } catch {
    // 错误提示已由 http 拦截器统一处理
  } finally {
    outboundLoading.value = false
  }
}

/** 数值格式化。 */
function fmt(value: number | null | undefined, digits = 2): string {
  return value == null ? '-' : value.toFixed(digits)
}

/**
 * 选中某条入库明细（用于高亮对应库位）。
 *
 * 放在脚本侧而不是模板内联：模板表达式里写 TS 类型标注会被模板编译器当作 JS 解析。
 *
 * @param row 表格当前行，取消选中时为 null
 */
function onAssignmentSelect(row: { locationId: number } | null): void {
  selectedAssignment.value = row?.locationId ?? null
}

/**
 * 选中某条出库订单（用于绘制其拣选路径）。
 *
 * @param row 表格当前行，取消选中时为 null
 */
function onOrderSelect(row: OutboundOrderResult | null): void {
  selectedOrderId.value = row?.orderId ?? null
}

/** 方案下拉展示文案。 */
function planLabel(plan: Plan): string {
  return `#${plan.id} ${plan.strategyName ?? plan.strategy} · ${plan.name ?? ''}（总 ${fmt(plan.totalDistance)} / 违规 ${plan.violations}）`
}
</script>

<template>
  <div class="wms-page">
    <el-card shadow="never">
      <div class="wms-search-bar">
        <span>仓库：</span>
        <el-select v-model="warehouseId" style="width: 220px" @change="onWarehouseChange">
          <el-option
            v-for="item in warehouses"
            :key="item.id"
            :label="`${item.name}（${item.code}）`"
            :value="item.id"
          />
        </el-select>
        <span>距离口径：</span>
        <el-select v-model="distanceMetric" style="width: 170px">
          <el-option label="曼哈顿距离（默认）" value="manhattan" />
          <el-option label="欧氏距离" value="euclidean" />
          <el-option label="三维折线距离" value="polyline" />
        </el-select>
        <span v-if="distanceMetric === 'polyline'">层高：</span>
        <el-input-number
          v-if="distanceMetric === 'polyline'"
          v-model="layerHeight"
          :min="0.1"
          :step="0.5"
          size="small"
          style="width: 110px"
        />
        <span class="wms-muted">当前仓库空闲库位 {{ flatLocations.filter((l) => l.status === 'free').length }} 个</span>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" class="sim__tabs">
      <!-- ============================ 入库仿真 ============================ -->
      <el-tab-pane label="① 入库策略仿真" name="inbound">
        <el-card shadow="never">
          <template #header>仿真配置（API-054 / API-055）</template>

          <div class="sim__section">
            <div class="sim__label">入库策略</div>
            <el-radio-group v-model="strategy">
              <el-radio-button v-for="item in strategies" :key="item.name" :value="item.name">
                {{ item.displayName }}
              </el-radio-button>
            </el-radio-group>
            <div v-if="currentStrategy" class="wms-muted sim__desc">
              {{ currentStrategy.description }}
            </div>
          </div>

          <div class="sim__section">
            <div class="sim__label">
              入库明细
              <el-button link type="primary" size="small" @click="toggleAll(true)">全选</el-button>
              <el-button link type="primary" size="small" @click="toggleAll(false)">全不选</el-button>
            </div>
            <el-table :data="rows" border stripe size="small" max-height="280">
              <el-table-column width="46">
                <template #default="{ row }">
                  <el-checkbox v-model="row.checked" />
                </template>
              </el-table-column>
              <el-table-column prop="skuCode" label="编码" width="110" />
              <el-table-column prop="name" label="货物" min-width="140" />
              <el-table-column prop="category" label="品类" width="90" />
              <el-table-column prop="weight" label="重量(kg)" width="100" />
              <el-table-column prop="turnoverRate" label="周转频次" width="100" />
              <el-table-column prop="priority" label="优先级" width="80" />
              <el-table-column label="入库数量" width="150">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="1" :max="9999" size="small" />
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="sim__actions">
            <el-button v-permission="'sim:run'" type="primary" :loading="inboundLoading" @click="runInbound">
              开始入库仿真
            </el-button>
            <el-button v-permission="'sim:run'" :disabled="!inboundResult" @click="resetInbound">
              清空重置
            </el-button>
            <span class="wms-muted">
              已选 {{ checkedRows.length }} 项；仿真只产出方案快照，<b>不改动库位占用状态</b>
              （执行仿真需要 <code>sim:run</code> 权限）
            </span>
          </div>
        </el-card>

        <el-card v-if="inboundResult" shadow="never">
          <template #header>
            仿真结果 · {{ inboundResult.simulationId }} · 方案 #{{ inboundResult.planId }}
            （{{ inboundResult.planNo }}）
          </template>

          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="策略">{{ inboundResult.strategyName }}</el-descriptions-item>
            <el-descriptions-item label="入库条目">{{ inboundResult.assignmentCount }}</el-descriptions-item>
            <el-descriptions-item label="总搬运路程">
              {{ fmt(inboundResult.totalDistance) }}
            </el-descriptions-item>
            <el-descriptions-item label="平均路程">
              {{ fmt(inboundResult.avgDistance) }}
            </el-descriptions-item>
            <el-descriptions-item label="高频货平均距离">
              {{ fmt(inboundResult.hotAvgDistance) }}
            </el-descriptions-item>
            <el-descriptions-item label="重货层位违规">
              <el-tag :type="inboundResult.violations > 0 ? 'danger' : 'success'" size="small">
                {{ inboundResult.violations }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">逐次选位与理由（FR-3.2）</el-divider>

          <el-table
            :data="inboundResult.assignments"
            border
            stripe
            size="small"
            highlight-current-row
            row-key="locationId"
            @current-change="onAssignmentSelect"
          >
            <el-table-column prop="skuCode" label="货物" width="110" />
            <el-table-column prop="code" label="分配库位" width="130" />
            <el-table-column prop="quantity" label="数量" width="80" />
            <el-table-column label="适配分" width="90">
              <template #default="{ row }">{{ fmt(row.score) }}</template>
            </el-table-column>
            <el-table-column prop="reason" label="选择理由" min-width="280" />
            <el-table-column label="告警" min-width="200">
              <template #default="{ row }">
                <el-tag v-if="row.warning" type="danger" size="small">{{ row.warning }}</el-tag>
                <span v-else class="wms-muted">—</span>
              </template>
            </el-table-column>
          </el-table>

          <el-divider content-position="left">本方案占用库位（平面图高亮）</el-divider>
          <WarehouseLayout
            :locations="flatLocations"
            :exit="exitPoint"
            :highlight="highlightIds"
            :height="360"
          />
        </el-card>
      </el-tab-pane>

      <!-- ============================ 出库仿真 ============================ -->
      <el-tab-pane label="② 出库仿真与统计" name="outbound">
        <el-card shadow="never">
          <template #header>订单集与方案（API-062 / API-049 / API-058）</template>

          <div class="sim__section">
            <div class="sim__label">随机测试订单集（API-062，可复现：同种子同结果）</div>
            <div class="wms-search-bar">
              <span>数量</span>
              <el-input-number v-model="orderForm.count" :min="1" :max="500" size="small" style="width: 120px" />
              <span>优先级</span>
              <el-input-number v-model="orderForm.priorityMin" :min="1" :max="5" size="small" style="width: 100px" />
              <span>~</span>
              <el-input-number v-model="orderForm.priorityMax" :min="1" :max="5" size="small" style="width: 100px" />
              <span>出库数量</span>
              <el-input-number v-model="orderForm.quantityMin" :min="1" size="small" style="width: 100px" />
              <span>~</span>
              <el-input-number v-model="orderForm.quantityMax" :min="1" size="small" style="width: 100px" />
              <span>随机种子</span>
              <el-input-number v-model="orderForm.randomSeed" :min="0" size="small" style="width: 140px" />
              <el-button v-permission="'sim:run'" type="primary" :loading="generating" @click="runGenerate">
                生成订单集
              </el-button>
            </div>
            <div v-if="generated" class="wms-muted">
              已生成 {{ generated.generated }} 张待出库订单，首单 {{ generated.firstOrderNo }}，
              种子 {{ generated.randomSeed }}
            </div>
          </div>

          <div class="sim__section">
            <div class="sim__label">拣选依据方案</div>
            <div class="wms-search-bar">
              <el-select v-model="selectedPlanId" clearable placeholder="按当前真实占用拣选" style="width: 420px">
                <el-option v-for="plan in plans" :key="plan.id" :label="planLabel(plan)" :value="plan.id" />
              </el-select>
              <span>移动速度（格/秒）</span>
              <el-input-number v-model="speed" :min="0.1" :step="0.5" size="small" style="width: 120px" />
              <el-button v-permission="'sim:run'" type="primary" :loading="outboundLoading" @click="runOutbound">
                开始出库仿真
              </el-button>
            </div>
            <div class="wms-muted">
              共 {{ plans.length }} 个可用方案；不选方案时按仓库当前真实占用拣选。
              想对比不同策略，可回到①用不同策略各跑一次入库仿真。
            </div>
          </div>
        </el-card>

        <el-card v-if="outboundResult" shadow="never">
          <template #header>
            路程统计（API-060） · {{ outboundResult.simulationId }}
            <span class="wms-muted">
              （库存来源：{{ outboundResult.stockSource === 'plan' ? '方案占用映射' : '仓库真实占用' }}）
            </span>
          </template>

          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="订单数">{{ outboundResult.stats.orderCount }}</el-descriptions-item>
            <el-descriptions-item label="总搬运路程">
              {{ fmt(outboundResult.stats.totalDistance) }}
            </el-descriptions-item>
            <el-descriptions-item label="平均每单路程">
              {{ fmt(outboundResult.stats.avgDistance) }}
            </el-descriptions-item>
            <el-descriptions-item label="高频货平均距离">
              {{ fmt(outboundResult.stats.hotAvgDistance) }}
            </el-descriptions-item>
            <el-descriptions-item label="耗时估算">
              {{ fmt(outboundResult.stats.estimatedTime, 1) }} 秒
            </el-descriptions-item>
            <el-descriptions-item label="移动速度">
              {{ fmt(outboundResult.stats.speed, 1) }} 格/秒
            </el-descriptions-item>
            <el-descriptions-item label="距离口径">
              {{ outboundResult.stats.distanceMetric }}
            </el-descriptions-item>
            <el-descriptions-item label="拣选方式">
              {{ outboundResult.pickingMode === 'single' ? '按单拣选' : outboundResult.pickingMode }}
            </el-descriptions-item>
          </el-descriptions>

          <el-divider content-position="left">单订单路程分布（前 20 单）</el-divider>
          <SeriesChart
            :categories="chartCategories"
            :series="chartSeries"
            :axis-names="['路程', '']"
            :height="320"
          />

          <el-divider content-position="left">逐单明细与拣选路径（API-059 / API-061）</el-divider>
          <el-row :gutter="16">
            <el-col :span="13">
              <el-table
                :data="outboundResult.orders"
                border
                stripe
                size="small"
                height="360"
                highlight-current-row
                @current-change="onOrderSelect"
              >
                <el-table-column prop="orderNo" label="订单号" width="150" />
                <el-table-column prop="skuCode" label="货物" width="100" />
                <el-table-column prop="quantity" label="数量" width="70" />
                <el-table-column prop="priority" label="优先级" width="80" />
                <el-table-column label="路程" width="80">
                  <template #default="{ row }">{{ fmt(row.distance) }}</template>
                </el-table-column>
                <el-table-column label="取货库位" min-width="160">
                  <template #default="{ row }">
                    <span v-if="row.picks.length === 0" class="wms-muted">—</span>
                    <el-tag v-for="pick in row.picks" :key="pick.locationId" size="small" class="sim__pick">
                      {{ pick.code }} ×{{ pick.quantity }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="告警" min-width="180">
                  <template #default="{ row }">
                    <el-tag v-if="row.warning" type="warning" size="small">{{ row.warning }}</el-tag>
                    <span v-else class="wms-muted">—</span>
                  </template>
                </el-table-column>
              </el-table>
            </el-col>
            <el-col :span="11">
              <div class="wms-muted sim__path-title">
                <template v-if="selectedOrder">
                  拣选路径：{{ selectedOrder.orderNo }}（路程 {{ fmt(selectedOrder.distance) }}）
                </template>
                <template v-else>点击左侧订单查看拣选路径</template>
              </div>
              <WarehouseLayout
                :locations="flatLocations"
                :exit="exitPoint"
                :highlight="pathHighlight"
                :path="pathPoints"
                :height="320"
              />
            </el-col>
          </el-row>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.sim__tabs {
  margin-top: 12px;
}

.sim__section {
  margin-bottom: 18px;
}

.sim__label {
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}

.sim__desc {
  margin-top: 6px;
}

.sim__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.sim__pick {
  margin-right: 6px;
}

.sim__path-title {
  margin-bottom: 8px;
}
</style>
