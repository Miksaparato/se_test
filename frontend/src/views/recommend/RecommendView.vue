<script setup lang="ts">
import { computed, ref } from 'vue'
import { adoptRecommendation, recommend } from '@/api/recommend'
import WarehouseLayoutPlaceholder from '@/components/warehouse/WarehouseLayoutPlaceholder.vue'
import { DEMO_SKUS, DEMO_WAREHOUSE_ID } from '@/constants/demo'
import type { AdoptResult, LocationScore, RecommendationDto } from '@/types'

const skuId = ref<number>(DEMO_SKUS[0].id)
const topN = ref<number>(10)
const loading = ref(false)
const result = ref<RecommendationDto | null>(null)
const adopted = ref<AdoptResult | null>(null)
const error = ref('')

const highlightIds = computed<number[]>(
  () => result.value?.candidates.map((c) => c.locationId) ?? [],
)

async function onSubmit() {
  loading.value = true
  error.value = ''
  result.value = null
  adopted.value = null
  try {
    result.value = await recommend({
      skuId: skuId.value,
      warehouseId: DEMO_WAREHOUSE_ID,
      topN: topN.value,
    })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function onAdopt() {
  if (!result.value) return
  error.value = ''
  try {
    adopted.value = await adoptRecommendation(result.value.recommendationId)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

function fmt(n: number): string {
  return n.toFixed(2)
}

function scoreBarWidth(score: number): string {
  return `${Math.max(0, Math.min(1, score)) * 100}%`
}

function rowClass(c: LocationScore): string {
  return result.value && c === result.value.candidates[0] ? 'best-row' : ''
}
</script>

<template>
  <div>
    <div class="card">
      <div class="card-title">入库推荐（B-F1）</div>
      <div class="form-row">
        <label for="sku">货物</label>
        <select id="sku" v-model.number="skuId">
          <option v-for="s in DEMO_SKUS" :key="s.id" :value="s.id">
            {{ s.skuCode }} - {{ s.name }}（{{ s.weight }}kg / 频次 {{ s.turnoverRate }}）
          </option>
        </select>

        <label for="topN">返回数量</label>
        <input id="topN" v-model.number="topN" type="number" min="1" max="50" style="width: 90px" />

        <button class="primary" :disabled="loading" @click="onSubmit">
          {{ loading ? '计算中…' : '生成推荐' }}
        </button>
      </div>
      <p class="muted">当前仓库：一号仓（id={{ DEMO_WAREHOUSE_ID }}）。TODO(a)：接入仓库/SKU 列表接口后改为动态选择。</p>
    </div>

    <div v-if="error" class="card">
      <div class="error">{{ error }}</div>
    </div>

    <div v-if="result" class="card">
      <div class="card-title">
        推荐结果 · {{ result.sku.skuCode }}（{{ result.sku.name }}）
        <span class="muted">编号 {{ result.recommendationId }}</span>
      </div>

      <table>
        <thead>
          <tr>
            <th>排名</th>
            <th>库位编码</th>
            <th>综合评分</th>
            <th>重量分</th>
            <th>频次分</th>
            <th>优先级分</th>
            <th>其他分</th>
            <th>推荐理由</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(c, i) in result.candidates" :key="c.locationId" :class="rowClass(c)">
            <td>{{ i + 1 }}</td>
            <td>{{ c.code }}</td>
            <td>
              <span class="score-bar" :style="{ width: scoreBarWidth(c.score) }"></span>
              {{ fmt(c.score) }}
            </td>
            <td>{{ fmt(c.subScores.weight) }}</td>
            <td>{{ fmt(c.subScores.freq) }}</td>
            <td>{{ fmt(c.subScores.priority) }}</td>
            <td>{{ fmt(c.subScores.other) }}</td>
            <td>{{ c.reason }}</td>
          </tr>
        </tbody>
      </table>

      <div class="form-row" style="margin-top: 16px">
        <button class="primary" :disabled="adopted !== null" @click="onAdopt">
          一键采用首位推荐
        </button>
        <span v-if="adopted" class="success">
          已采用库位 {{ adopted.code }}（评分 {{ fmt(adopted.score) }}，状态 {{ adopted.status }}）
        </span>
      </div>
    </div>

    <div class="card">
      <div class="card-title">平面图高亮（B-F2，占位）</div>
      <WarehouseLayoutPlaceholder :locations="[]" :exit="{ x: 0, y: 0 }" :highlight="highlightIds" />
    </div>
  </div>
</template>

<style scoped>
.best-row {
  background: #ecf5ff;
}
</style>
