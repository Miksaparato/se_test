<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

/**
 * ECharts 统一封装（《代码规范》5.6：图表用 ECharts，封装统一主题与配色；禁止在页面内散写 option）。
 *
 * 页面只提供**数据**（类目 + 系列），不接触 `option`：这样主题、配色、网格、
 * 提示框样式只在这里维护一次，页面之间不会出现「同一张图两种观感」。
 *
 * 负责人：c（公共图表组件，b 的方案对比页也复用）
 */

/** 一个数据系列。 */
interface ChartSeries {
  name: string
  type: 'bar' | 'line'
  data: number[]
  /** 使用第几个 y 轴（0 左轴 / 1 右轴），默认 0 */
  axis?: number
  /** 自定义颜色，缺省用主题色板 */
  color?: string
  /** 数值格式化小数位，默认 1 */
  digits?: number
}

const props = withDefaults(
  defineProps<{
    /** x 轴类目 */
    categories: string[]
    /** 数据系列 */
    series: ChartSeries[]
    /** 图表标题 */
    title?: string
    /** 左右两个 y 轴的名称，给第二个即启用双轴 */
    axisNames?: [string] | [string, string]
    /** 画布高度（px） */
    height?: number
  }>(),
  {
    title: '',
    axisNames: () => [''],
    height: 340,
  },
)

/** 统一色板，保持全局观感一致。 */
const PALETTE = ['#409eff', '#e6a23c', '#67c23a', '#f56c6c', '#909399']

const containerRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null
let observer: ResizeObserver | null = null

/** 组装 option（唯一出现 option 的地方）。 */
function buildOption(): echarts.EChartsOption {
  const dualAxis = props.series.some((item) => (item.axis ?? 0) === 1)
  const yAxis: echarts.EChartsOption['yAxis'] = dualAxis
    ? [
        { type: 'value', name: props.axisNames[0] ?? '' },
        { type: 'value', name: props.axisNames[1] ?? '', splitLine: { show: false } },
      ]
    : { type: 'value', name: props.axisNames[0] ?? '' }

  return {
    title: props.title ? { text: props.title, left: 'center', textStyle: { fontSize: 14 } } : undefined,
    color: PALETTE,
    tooltip: { trigger: 'axis', valueFormatter: (value) => Number(value).toFixed(1) },
    legend: { bottom: 0, data: props.series.map((item) => item.name) },
    grid: { left: 64, right: dualAxis ? 64 : 24, top: props.title ? 48 : 24, bottom: 48 },
    xAxis: { type: 'category', data: props.categories, axisLabel: { interval: 0, rotate: 0 } },
    yAxis,
    series: props.series.map((item, index) => ({
      name: item.name,
      type: item.type,
      yAxisIndex: item.axis ?? 0,
      data: item.data,
      barMaxWidth: 48,
      smooth: item.type === 'line',
      itemStyle: { color: item.color ?? PALETTE[index % PALETTE.length] },
      label: { show: item.type === 'bar', position: 'top', fontSize: 10 },
    })),
  }
}

/** 渲染/更新图表。 */
function render(): void {
  if (!containerRef.value || props.categories.length === 0) {
    chart?.clear()
    return
  }
  if (!chart) {
    chart = echarts.init(containerRef.value)
  }
  chart.setOption(buildOption(), true)
  chart.resize()
}

onMounted(() => {
  render()
  if (typeof ResizeObserver !== 'undefined' && containerRef.value) {
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(containerRef.value)
  }
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  chart?.dispose()
  chart = null
})

watch(() => [props.categories, props.series, props.title, props.axisNames], render, { deep: true })
</script>

<template>
  <div v-if="categories.length === 0" class="chart-empty">暂无可展示的数据</div>
  <div v-else ref="containerRef" class="chart" :style="{ height: `${height}px` }" />
</template>

<style scoped>
.chart {
  width: 100%;
}

.chart-empty {
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 13px;
  background: #fafafa;
  border-radius: 4px;
}
</style>
