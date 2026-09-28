<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { calibrate, getRules, getWeights, updateRules, updateWeights } from '@/api/config'
import { listSkus, listWarehouses } from '@/api/data'
import type {
  CalibrateResult,
  ScoreWeightConfig,
  Sku,
  StorageRuleConfig,
  Warehouse,
} from '@/types'

/**
 * 权重与规则配置页（B-F4 / FR-2.3 / B-B3、B-B4、B-B9）。
 *
 * 管理员调整评分权重与库位分层规则，并可「参数校准」按新参数重新评分预览（不落库）。
 * 权重之和须为 1，否则后端返回 42211。
 *
 * 负责人：b
 */
const warehouses = ref<Warehouse[]>([])
const skus = ref<Sku[]>([])

const weights = ref<ScoreWeightConfig>({ weight: 0.3, freq: 0.4, priority: 0.2, other: 0.1 })
const rules = ref<StorageRuleConfig>({
  heavyWeightThreshold: 100,
  maxLayerForHeavy: 2,
  maxWeightNorm: 200,
  maxTurnover: 1,
  maxPriority: 5,
  goldenZoneRadius: 8,
  categoryMatchEnabled: true,
})

const saving = ref(false)
const calLoading = ref(false)
const calResult = ref<CalibrateResult | null>(null)

const calibration = reactive({
  skuId: undefined as number | undefined,
  warehouseId: undefined as number | undefined,
  topN: 10,
})

/** 权重之和（用于实时提示是否满足 1）。 */
const weightSum = computed(
  () => weights.value.weight + weights.value.freq + weights.value.priority + weights.value.other,
)

/** 加载当前配置与下拉选项。 */
async function load(): Promise<void> {
  const [weightConfig, ruleConfig, warehousePage, skuPage] = await Promise.all([
    getWeights(),
    getRules(),
    listWarehouses({ page: 1, page_size: 100 }),
    listSkus({ page: 1, page_size: 100 }),
  ])
  weights.value = weightConfig
  rules.value = ruleConfig
  warehouses.value = warehousePage.list
  skus.value = skuPage.list
  calibration.warehouseId = warehousePage.list[0]?.id
  calibration.skuId = skuPage.list[0]?.id
}

/** 保存权重（API-045）。 */
async function saveWeights(): Promise<void> {
  saving.value = true
  try {
    weights.value = await updateWeights(weights.value)
    ElMessage.success('权重已保存')
  } catch {
    // 错误提示已由 http 拦截器统一处理
  } finally {
    saving.value = false
  }
}

/** 保存分层规则（API-047）。 */
async function saveRules(): Promise<void> {
  saving.value = true
  try {
    rules.value = await updateRules(rules.value)
    ElMessage.success('分层规则已保存')
  } catch {
    // 错误提示已由 http 拦截器统一处理
  } finally {
    saving.value = false
  }
}

/** 参数校准（API-048）：按当前表单（可能未保存）的参数重新评分预览。 */
async function onCalibrate(): Promise<void> {
  if (!calibration.skuId || !calibration.warehouseId) {
    ElMessage.warning('请先选择仓库与货物')
    return
  }
  calLoading.value = true
  try {
    calResult.value = await calibrate({
      skuId: calibration.skuId,
      warehouseId: calibration.warehouseId,
      topN: calibration.topN,
      weights: { ...weights.value },
      rules: { ...rules.value },
    })
  } catch {
    calResult.value = null
  } finally {
    calLoading.value = false
  }
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
    await load()
  } catch {
    // 错误提示已由 http 拦截器统一处理
  }
})
</script>

<template>
  <div class="wms-page">
    <el-card shadow="never">
      <template #header>
        评分权重（API-044 / API-045）
        <el-tag
          class="config__sum"
          size="small"
          :type="Math.abs(weightSum - 1) < 1e-6 ? 'success' : 'danger'"
        >
          当前之和 {{ fmt(weightSum) }}
        </el-tag>
      </template>

      <div class="wms-search-bar">
        <span>重量</span>
        <el-input-number v-model="weights.weight" :min="0" :max="1" :step="0.05" size="small" />
        <span>周转频次</span>
        <el-input-number v-model="weights.freq" :min="0" :max="1" :step="0.05" size="small" />
        <span>出库优先级</span>
        <el-input-number v-model="weights.priority" :min="0" :max="1" :step="0.05" size="small" />
        <span>其他（品类/连续性）</span>
        <el-input-number v-model="weights.other" :min="0" :max="1" :step="0.05" size="small" />
        <el-button type="primary" :loading="saving" @click="saveWeights">保存权重</el-button>
      </div>
      <div class="wms-muted">
        四项之和必须为 1（默认 0.30 / 0.40 / 0.20 / 0.10）；保存后推荐引擎与「智能推荐」仿真策略
        会立刻使用新权重（FR-2.3）。
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>库位分层规则（API-046 / API-047）</template>

      <div class="wms-search-bar">
        <span>重货阈值(kg)</span>
        <el-input-number v-model="rules.heavyWeightThreshold" :min="1" size="small" />
        <span>重货最高层号</span>
        <el-input-number v-model="rules.maxLayerForHeavy" :min="1" :max="99" size="small" />
        <span>重量归一上限</span>
        <el-input-number v-model="rules.maxWeightNorm" :min="1" size="small" />
        <span>频次归一上限</span>
        <el-input-number v-model="rules.maxTurnover" :min="0.1" :step="0.1" size="small" />
      </div>
      <div class="wms-search-bar">
        <span>优先级上限</span>
        <el-input-number v-model="rules.maxPriority" :min="1" :max="10" size="small" />
        <span>黄金区半径</span>
        <el-input-number v-model="rules.goldenZoneRadius" :min="0" :step="0.5" size="small" />
        <el-switch v-model="rules.categoryMatchEnabled" active-text="启用品类匹配" />
        <el-button type="primary" :loading="saving" @click="saveRules">保存规则</el-button>
      </div>
      <div class="wms-muted">
        规则示例：重货（≥ {{ rules.heavyWeightThreshold }}kg）只允许存放在层号 ≤
        {{ rules.maxLayerForHeavy }} 的库位——推荐引擎会直接过滤，仿真引擎会按策略判定并计入
        「重货层位违规数」。
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>参数校准（API-048：按新参数重新评分预览，不落库）</template>

      <div class="wms-search-bar">
        <span>仓库：</span>
        <el-select v-model="calibration.warehouseId" style="width: 200px">
          <el-option
            v-for="item in warehouses"
            :key="item.id"
            :label="`${item.name}（${item.code}）`"
            :value="item.id"
          />
        </el-select>
        <span>货物：</span>
        <el-select v-model="calibration.skuId" filterable style="width: 300px">
          <el-option
            v-for="sku in skus"
            :key="sku.id"
            :label="`${sku.skuCode} - ${sku.name}`"
            :value="sku.id"
          />
        </el-select>
        <span>返回数量</span>
        <el-input-number v-model="calibration.topN" :min="1" :max="50" size="small" style="width: 120px" />
        <el-button type="primary" :loading="calLoading" @click="onCalibrate">开始校准</el-button>
      </div>

      <el-table v-if="calResult && calResult.candidates.length" :data="calResult.candidates" border stripe size="small">
        <el-table-column type="index" label="排名" width="70" />
        <el-table-column prop="code" label="库位编码" width="130" />
        <el-table-column label="综合评分" width="110">
          <template #default="{ row }">{{ fmt(row.score) }}</template>
        </el-table-column>
        <el-table-column label="重量分" width="100">
          <template #default="{ row }">{{ fmt(row.subScores.weight) }}</template>
        </el-table-column>
        <el-table-column label="频次分" width="100">
          <template #default="{ row }">{{ fmt(row.subScores.freq) }}</template>
        </el-table-column>
        <el-table-column label="优先级分" width="110">
          <template #default="{ row }">{{ fmt(row.subScores.priority) }}</template>
        </el-table-column>
        <el-table-column label="其他分" width="100">
          <template #default="{ row }">{{ fmt(row.subScores.other) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="理由" min-width="240" />
      </el-table>
      <el-empty
        v-else-if="calResult"
        description="当前参数下无满足约束的库位（重货层高或容量规则可能过滤了全部空位）"
        :image-size="70"
      />
      <div v-else class="wms-muted">
        校准会用表单里的权重与规则重新评分，方便在保存前对比「参数改动带来的排序变化」。
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.config__sum {
  margin-left: 10px;
}
</style>
