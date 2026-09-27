<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { calibrate, getRules, getWeights, updateRules, updateWeights } from '@/api/config'
import { DEMO_SKUS, DEMO_WAREHOUSE_ID } from '@/constants/demo'
import type { CalibrateResult, ScoreWeightConfig, StorageRuleConfig } from '@/types'

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

const msg = ref('')
const error = ref('')

const calSkuId = ref<number>(DEMO_SKUS[0].id)
const calTopN = ref<number>(10)
const calLoading = ref(false)
const calResult = ref<CalibrateResult | null>(null)

onMounted(async () => {
  await load()
})

async function load() {
  error.value = ''
  try {
    weights.value = await getWeights()
    rules.value = await getRules()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function saveWeights() {
  error.value = ''
  msg.value = ''
  try {
    weights.value = await updateWeights(weights.value)
    msg.value = '权重已保存'
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function saveRules() {
  error.value = ''
  msg.value = ''
  try {
    rules.value = await updateRules(rules.value)
    msg.value = '分层规则已保存'
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function onCalibrate() {
  calLoading.value = true
  error.value = ''
  calResult.value = null
  try {
    // 调参预览：用当前表单（可能未保存）的权重/规则重新评分，不落库（B-B9）。
    calResult.value = await calibrate({
      skuId: calSkuId.value,
      warehouseId: DEMO_WAREHOUSE_ID,
      topN: calTopN.value,
      weights: { ...weights.value },
      rules: { ...rules.value },
    })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    calLoading.value = false
  }
}

function fmt(n: number): string {
  return n.toFixed(2)
}
</script>

<template>
  <div>
    <div class="card">
      <div class="card-title">评分权重（API-044/045）</div>
      <div class="form-row">
        <label>重量 <input v-model.number="weights.weight" type="number" step="0.01" style="width: 80px" /></label>
        <label>周转频次 <input v-model.number="weights.freq" type="number" step="0.01" style="width: 80px" /></label>
        <label>优先级 <input v-model.number="weights.priority" type="number" step="0.01" style="width: 80px" /></label>
        <label>其他 <input v-model.number="weights.other" type="number" step="0.01" style="width: 80px" /></label>
        <button class="primary" @click="saveWeights">保存权重</button>
      </div>
      <p class="muted">四项之和须为 1（当前 {{ fmt(weights.weight + weights.freq + weights.priority + weights.other) }}）。默认 0.30 / 0.40 / 0.20 / 0.10。</p>
    </div>

    <div class="card">
      <div class="card-title">库位分层规则（API-046/047）</div>
      <div class="form-row">
        <label>重货阈值(kg) <input v-model.number="rules.heavyWeightThreshold" type="number" style="width: 80px" /></label>
        <label>重货最高层号 <input v-model.number="rules.maxLayerForHeavy" type="number" min="1" style="width: 80px" /></label>
        <label>重量归一上限 <input v-model.number="rules.maxWeightNorm" type="number" style="width: 80px" /></label>
        <label>频次归一上限 <input v-model.number="rules.maxTurnover" type="number" step="0.1" style="width: 80px" /></label>
      </div>
      <div class="form-row">
        <label>优先级上限 <input v-model.number="rules.maxPriority" type="number" style="width: 80px" /></label>
        <label>黄金区半径 <input v-model.number="rules.goldenZoneRadius" type="number" step="0.5" style="width: 80px" /></label>
        <label><input v-model="rules.categoryMatchEnabled" type="checkbox" /> 启用品类匹配</label>
        <button class="primary" @click="saveRules">保存规则</button>
      </div>
      <p class="muted">规则示例：重货（≥ {{ rules.heavyWeightThreshold }}kg）只允许存放在层号 ≤ {{ rules.maxLayerForHeavy }} 的库位。</p>
    </div>

    <div class="card">
      <div class="card-title">参数校准（API-048，调参重新评分预览）</div>
      <div class="form-row">
        <label>货物</label>
        <select v-model.number="calSkuId">
          <option v-for="s in DEMO_SKUS" :key="s.id" :value="s.id">{{ s.skuCode }} - {{ s.name }}</option>
        </select>
        <label>返回数量</label>
        <input v-model.number="calTopN" type="number" min="1" max="50" style="width: 90px" />
        <button class="primary" :disabled="calLoading" @click="onCalibrate">
          {{ calLoading ? '计算中…' : '开始校准' }}
        </button>
      </div>

      <table v-if="calResult && calResult.candidates.length">
        <thead>
          <tr>
            <th>排名</th>
            <th>库位编码</th>
            <th>综合评分</th>
            <th>重量分</th>
            <th>频次分</th>
            <th>优先级分</th>
            <th>其他分</th>
            <th>理由</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(c, i) in calResult.candidates" :key="c.locationId">
            <td>{{ i + 1 }}</td>
            <td>{{ c.code }}</td>
            <td>{{ fmt(c.score) }}</td>
            <td>{{ fmt(c.subScores.weight) }}</td>
            <td>{{ fmt(c.subScores.freq) }}</td>
            <td>{{ fmt(c.subScores.priority) }}</td>
            <td>{{ fmt(c.subScores.other) }}</td>
            <td>{{ c.reason }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else-if="calResult" class="muted">当前参数下无满足约束的库位。</p>
    </div>

    <div v-if="msg" class="success">{{ msg }}</div>
    <div v-if="error" class="error">{{ error }}</div>
  </div>
</template>
