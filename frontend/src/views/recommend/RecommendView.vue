<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getWarehouseLayout, listSkus, listWarehouses } from '@/api/data'
import { adoptRecommendation, recommend } from '@/api/recommend'
import WarehouseLayout from '@/components/warehouse/WarehouseLayout.vue'
import type {
  AdoptResult,
  LayoutLocation,
  RecommendationDto,
  Sku,
  Warehouse,
  WarehouseLayout as WarehouseLayoutData,
} from '@/types'

/**
 * 入库推荐页（界面需求 8.3 / B-F1、B-F2）。
 *
 * 展示「推荐列表（评分降序 + 分项得分 + 理由）」与「平面图高亮」两种视图，
 * 并支持一键采用推荐完成入库（API-041 ~ API-043）。
 *
 * 平面图复用 c 的公共组件（C-F1，冻结契约 COM-5），本页只负责传 `highlight`。
 *
 * 负责人：b
 */
const warehouses = ref<Warehouse[]>([])
const skus = ref<Sku[]>([])
const layout = ref<WarehouseLayoutData | null>(null)

const query = reactive({
  warehouseId: undefined as number | undefined,
  skuId: undefined as number | undefined,
  topN: 10,
})

const loading = ref(false)
const adopting = ref(false)
const result = ref<RecommendationDto | null>(null)
const adopted = ref<AdoptResult | null>(null)

/** 推荐候选库位 id（平面图高亮，B-F2）。 */
const highlightIds = computed<number[]>(
  () => result.value?.candidates.map((item) => item.locationId) ?? [],
)

/** 布局库位平铺列表。 */
const flatLocations = computed<LayoutLocation[]>(
  () => (layout.value?.racks ?? []).flatMap((rack) => rack.locations),
)

/** 加载仓库与货物下拉。 */
async function loadOptions(): Promise<void> {
  const [warehousePage, skuPage] = await Promise.all([
    listWarehouses({ page: 1, page_size: 100 }),
    listSkus({ page: 1, page_size: 100 }),
  ])
  warehouses.value = warehousePage.list
  skus.value = skuPage.list
  query.warehouseId = warehousePage.list[0]?.id
  query.skuId = skuPage.list[0]?.id
  if (query.warehouseId) {
    layout.value = await getWarehouseLayout(query.warehouseId)
  }
}

/** 切换仓库时刷新平面图。 */
async function onWarehouseChange(): Promise<void> {
  result.value = null
  adopted.value = null
  layout.value = query.warehouseId ? await getWarehouseLayout(query.warehouseId) : null
}

/** 计算推荐（API-041）。 */
async function onSubmit(): Promise<void> {
  if (!query.skuId || !query.warehouseId) {
    ElMessage.warning('请先选择仓库与货物')
    return
  }
  loading.value = true
  adopted.value = null
  try {
    result.value = await recommend({
      skuId: query.skuId,
      warehouseId: query.warehouseId,
      topN: query.topN,
    })
  } catch {
    result.value = null
  } finally {
    loading.value = false
  }
}

/** 一键采用首位推荐（API-043）。 */
async function onAdopt(): Promise<void> {
  if (!result.value) {
    return
  }
  adopting.value = true
  try {
    adopted.value = await adoptRecommendation(result.value.recommendationId)
    ElMessage.success(`已采用库位 ${adopted.value.code}`)
    if (query.warehouseId) {
      layout.value = await getWarehouseLayout(query.warehouseId)
    }
  } catch {
    // 错误提示已由 http 拦截器统一处理
  } finally {
    adopting.value = false
  }
}

/**
 * 评分条宽度（按综合评分 0~1 折算）。
 *
 * @param score 综合评分
 */
function scoreBarWidth(score: number): string {
  return `${Math.max(0, Math.min(1, score)) * 100}%`
}

/**
 * 数值格式化。
 *
 * @param value 数值
 * @param digits 小数位
 */
function fmt(value: number | null | undefined, digits = 3): string {
  return value == null ? '-' : value.toFixed(digits)
}

onMounted(async () => {
  try {
    await loadOptions()
  } catch {
    // 错误提示已由 http 拦截器统一处理
  }
})
</script>

<template>
  <div class="wms-page">
    <el-card shadow="never">
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

        <span>货物：</span>
        <el-select v-model="query.skuId" filterable style="width: 320px">
          <el-option
            v-for="sku in skus"
            :key="sku.id"
            :label="`${sku.skuCode} - ${sku.name}（${sku.weight}kg / 频次 ${sku.turnoverRate} / 优先级 ${sku.priority}）`"
            :value="sku.id"
          />
        </el-select>

        <span>返回数量</span>
        <el-input-number v-model="query.topN" :min="1" :max="50" size="small" style="width: 120px" />

        <el-button type="primary" :loading="loading" @click="onSubmit">生成推荐</el-button>
      </div>
      <div class="wms-muted">
        推荐结果按综合评分降序展示，并给出每个候选库位、各分项得分与可读理由（FR-2.2）。
      </div>
    </el-card>

    <el-card v-if="result" shadow="never">
      <template #header>
        推荐结果 · {{ result.sku.skuCode }}（{{ result.sku.name }}）
        <span class="wms-muted">编号 {{ result.recommendationId }}</span>
      </template>

      <el-table :data="result.candidates" border stripe size="small">
        <el-table-column type="index" label="排名" width="70" />
        <el-table-column prop="code" label="库位编码" width="130" />
        <el-table-column label="综合评分" width="190">
          <template #default="{ row }">
            <div class="recommend__score">
              <span class="score-bar" :style="{ width: scoreBarWidth(row.score) }" />
              <span>{{ fmt(row.score) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="重量分" width="90">
          <template #default="{ row }">{{ fmt(row.subScores.weight) }}</template>
        </el-table-column>
        <el-table-column label="频次分" width="90">
          <template #default="{ row }">{{ fmt(row.subScores.freq) }}</template>
        </el-table-column>
        <el-table-column label="优先级分" width="100">
          <template #default="{ row }">{{ fmt(row.subScores.priority) }}</template>
        </el-table-column>
        <el-table-column label="其他分" width="90">
          <template #default="{ row }">{{ fmt(row.subScores.other) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="推荐理由" min-width="260" />
      </el-table>

      <div class="recommend__actions">
        <el-button type="primary" :loading="adopting" :disabled="adopted !== null" @click="onAdopt">
          一键采用首位推荐
        </el-button>
        <span v-if="adopted" class="success">
          已采用库位 {{ adopted.code }}（评分 {{ fmt(adopted.score) }}，状态 {{ adopted.status }}）
        </span>
      </div>

      <el-divider content-position="left">平面图高亮（B-F2）</el-divider>
      <WarehouseLayout
        :locations="flatLocations"
        :exit="layout?.exit ?? { x: 0, y: 0 }"
        :highlight="highlightIds"
        :height="360"
      />
    </el-card>

    <el-empty v-else-if="!loading" description="选择货物后点击「生成推荐」" />
  </div>
</template>

<style scoped>
.recommend__score {
  display: flex;
  align-items: center;
  gap: 8px;
}

.recommend__score .score-bar {
  width: 60px;
  height: 8px;
  border-radius: 4px;
  background: #409eff;
  display: inline-block;
}

.recommend__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}
</style>
