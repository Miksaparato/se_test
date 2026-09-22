<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { comparePlans, exportReportUrl, listPlans } from '@/api/compare'
import type { CompareResult, Plan, PlanMetric } from '@/types'

const plans = ref<Plan[]>([])
const selectedIds = ref<number[]>([])
const loading = ref(false)
const result = ref<CompareResult | null>(null)
const error = ref('')

const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

onMounted(async () => {
  await loadPlans()
})

onBeforeUnmount(() => {
  chart?.dispose()
})

async function loadPlans() {
  error.value = ''
  try {
    plans.value = await listPlans()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

function togglePlan(id: number) {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) {
    selectedIds.value.splice(idx, 1)
  } else {
    selectedIds.value.push(id)
  }
}

async function onCompare() {
  if (selectedIds.value.length < 2) {
    error.value = '请至少选择两个方案进行对比'
    return
  }
  loading.value = true
  error.value = ''
  try {
    result.value = await comparePlans({ planIds: selectedIds.value })
    await nextTick()
    renderChart(result.value.metrics)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

function renderChart(metrics: PlanMetric[]) {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  chart.setOption({
    title: { text: '方案指标对比', left: 'center' },
    tooltip: { trigger: 'axis' },
    legend: { data: ['总搬运路程', '高频货平均距离'], bottom: 0 },
    grid: { left: 60, right: 60, top: 50, bottom: 50 },
    xAxis: { type: 'category', data: metrics.map((m) => m.name) },
    yAxis: [
      { type: 'value', name: '总路程' },
      { type: 'value', name: '高频货距离' },
    ],
    series: [
      {
        name: '总搬运路程',
        type: 'bar',
        data: metrics.map((m) => m.totalDistance),
        barMaxWidth: 60,
        itemStyle: { color: '#409eff' },
      },
      {
        name: '高频货平均距离',
        type: 'line',
        yAxisIndex: 1,
        data: metrics.map((m) => m.hotAvgDistance),
        itemStyle: { color: '#f56c6c' },
      },
    ],
  })
}

function fmt(n: number): string {
  return n.toFixed(1)
}
</script>

<template>
  <div>
    <div class="card">
      <div class="card-title">分配方案列表（API-049）</div>
      <table>
        <thead>
          <tr>
            <th>对比</th>
            <th>方案</th>
            <th>策略</th>
            <th>总搬运路程</th>
            <th>平均路程</th>
            <th>高频货平均距离</th>
            <th>重货违规数</th>
            <th>报告导出</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in plans" :key="p.id">
            <td>
              <input type="checkbox" :checked="selectedIds.includes(p.id)" @change="togglePlan(p.id)" />
            </td>
            <td>{{ p.name }}</td>
            <td>{{ p.strategy }}</td>
            <td>{{ fmt(p.totalDistance) }}</td>
            <td>{{ fmt(p.avgDistance) }}</td>
            <td>{{ fmt(p.hotAvgDistance) }}</td>
            <td>{{ p.violations }}</td>
            <td>
              <a :href="exportReportUrl(p.id, 'md')" download>MD</a>
              <span class="muted"> / </span>
              <a :href="exportReportUrl(p.id, 'csv')" download>CSV</a>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="form-row" style="margin-top: 16px">
        <button class="primary" :disabled="loading" @click="onCompare">
          {{ loading ? '对比中…' : '对比所选方案' }}
        </button>
        <span class="muted">已选 {{ selectedIds.length }} 个方案</span>
      </div>
    </div>

    <div v-if="error" class="error" style="margin-bottom: 12px">{{ error }}</div>

    <div v-if="result" class="card">
      <div class="card-title">对比结果（API-051）</div>
      <table>
        <thead>
          <tr>
            <th>方案</th>
            <th>策略</th>
            <th>总搬运路程</th>
            <th>平均路程</th>
            <th>高频货平均距离</th>
            <th>重货违规数</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in result.metrics" :key="m.planId">
            <td>{{ m.name }}</td>
            <td>{{ m.strategy }}</td>
            <td>{{ fmt(m.totalDistance) }}</td>
            <td>{{ fmt(m.avgDistance) }}</td>
            <td>{{ fmt(m.hotAvgDistance) }}</td>
            <td>{{ m.violations }}</td>
          </tr>
        </tbody>
      </table>

      <div ref="chartRef" style="width: 100%; height: 340px; margin-top: 16px"></div>

      <div class="card-title" style="margin-top: 16px">优化建议（API-052）</div>
      <p>{{ result.suggestion }}</p>
    </div>
  </div>
</template>
