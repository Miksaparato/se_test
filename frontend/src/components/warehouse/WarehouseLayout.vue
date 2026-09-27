<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import type { LayoutLocation } from '@/types'

/**
 * 仓库平面图公共组件（C-F1，冻结契约 COM-5，《代码规范》5.2）。
 *
 * a、b、c 三方共用**同一个**组件，不允许各自复制一份：
 * - a 的仓库总览页：只传 `locations` 与 `exit`；
 * - b 的入库推荐页：额外传 `highlight`（推荐库位高亮）；
 * - c 的仿真页：额外传 `path`（拣选路径）与 `highlight`（本方案占用的库位）。
 *
 * 用 **Canvas** 绘制而不是 DOM 节点：万级库位下 DOM 方案会直接卡死（NFR-1 / 《代码规范》5.6）。
 *
 * props / emits 契约：
 * | props | 类型 | 说明 |
 * | --- | --- | --- |
 * | locations | LayoutLocation[] | 库位（含坐标、层、状态） |
 * | exit | {x,y} | 出库口坐标 |
 * | highlight | number[] | 高亮库位 id（推荐结果 / 方案占用） |
 * | path | {x,y}[] | 拣选路径折线 |
 * | layer | number \| null | 只渲染该层；null 表示全部层叠在一起 |
 * | cellSize | number | 单元格像素边长（默认 14） |
 *
 * emits：`location-click(location)`、`location-hover(location | null)`
 *
 * 负责人：c
 */

/**
 * 平面图库位节点。
 *
 * 直接复用 a 的 `LayoutLocation`（API-021 布局节点）：三方消费同一类型，
 * 避免各写各的接口导致 props 对不上（COM-5 契约）。
 */
type PlaneLocation = LayoutLocation

const props = withDefaults(
  defineProps<{
    locations: PlaneLocation[]
    exit?: { x: number; y: number } | null
    highlight?: number[]
    path?: Array<{ x: number; y: number }>
    layer?: number | null
    cellSize?: number
    height?: number
    /** 是否绘制库位编码（库位少时可读性更好） */
    showCode?: boolean
  }>(),
  {
    exit: () => ({ x: 0, y: 0 }),
    highlight: () => [],
    path: () => [],
    layer: null,
    cellSize: 16,
    height: 420,
    showCode: false,
  },
)

const emit = defineEmits<{
  (e: 'location-click', location: PlaneLocation): void
  (e: 'location-hover', location: PlaneLocation | null): void
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
let observer: ResizeObserver | null = null

/** 状态配色：与图例保持一致。 */
const STATUS_COLOR: Record<string, string> = {
  free: '#e8f3ff',
  occupied: '#67c23a',
  disabled: '#c0c4cc',
}
const STATUS_BORDER: Record<string, string> = {
  free: '#a0cfff',
  occupied: '#529b2e',
  disabled: '#909399',
}
const HIGHLIGHT_COLOR = '#f56c6c'
const PATH_COLOR = '#e6a23c'

/** 当前层可选值（用于页面上的层切换）。 */
const availableLayers = computed(() => {
  const layers = new Set<number>()
  props.locations.forEach((item) => layers.add(item.layer ?? 1))
  return [...layers].sort((a, b) => a - b)
})

/** 参与渲染的库位（按 layer 过滤）。 */
const visibleLocations = computed(() =>
  props.layer == null
    ? props.locations
    : props.locations.filter((item) => (item.layer ?? 1) === props.layer),
)

/** 画布像素尺寸与坐标范围。 */
interface Viewport {
  minX: number
  minY: number
  cols: number
  rows: number
  cell: number
  offsetX: number
  offsetY: number
  width: number
  height: number
}

/**
 * 计算视口：把库位与出库口一起纳入边界，并留出一格边距。
 *
 * @param width 画布 CSS 宽度
 * @returns 视口参数
 */
function computeViewport(width: number): Viewport {
  const cell = props.cellSize
  const items = visibleLocations.value
  const exit = props.exit ?? { x: 0, y: 0 }
  const xs = items.map((item) => item.x).concat(exit.x)
  const ys = items.map((item) => item.y).concat(exit.y)
  const minX = xs.length ? Math.min(...xs) : 0
  const maxX = xs.length ? Math.max(...xs) : 1
  const minY = ys.length ? Math.min(...ys) : 0
  const maxY = ys.length ? Math.max(...ys) : 1
  const cols = Math.max(1, maxX - minX + 1)
  const rows = Math.max(1, maxY - minY + 1)
  return {
    minX,
    minY,
    cols,
    rows,
    cell,
    offsetX: cell,
    // 平面图按「y 向上」的仓库坐标系绘制，画布 y 轴向下，故底部留边
    offsetY: cell,
    width: Math.max(width, (cols + 2) * cell),
    height: Math.max(props.height, (rows + 2) * cell),
  }
}

/**
 * 库位中心像素坐标。
 *
 * @param location 库位
 * @param view 视口
 * @returns 画布像素坐标
 */
function centerOf(location: { x: number; y: number }, view: Viewport): { cx: number; cy: number } {
  const col = location.x - view.minX
  const row = location.y - view.minY
  return {
    cx: view.offsetX + col * view.cell + view.cell / 2,
    cy: view.height - view.offsetY - row * view.cell - view.cell / 2,
  }
}

/** 重绘画布。 */
function draw(): void {
  const canvas = canvasRef.value
  if (!canvas) {
    return
  }
  const containerWidth = canvas.parentElement?.clientWidth ?? 800
  const view = computeViewport(containerWidth)
  const dpr = window.devicePixelRatio || 1

  canvas.width = Math.round(view.width * dpr)
  canvas.height = Math.round(view.height * dpr)
  canvas.style.width = `${view.width}px`
  canvas.style.height = `${view.height}px`

  const ctx = canvas.getContext('2d')
  if (!ctx) {
    return
  }
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  ctx.clearRect(0, 0, view.width, view.height)

  // 背景与网格
  ctx.fillStyle = '#fbfcfe'
  ctx.fillRect(0, 0, view.width, view.height)
  ctx.strokeStyle = '#eef1f6'
  ctx.lineWidth = 1
  for (let col = 0; col <= view.cols; col++) {
    const px = view.offsetX + col * view.cell
    ctx.beginPath()
    ctx.moveTo(px, view.height - view.offsetY)
    ctx.lineTo(px, view.height - view.offsetY - view.rows * view.cell)
    ctx.stroke()
  }
  for (let row = 0; row <= view.rows; row++) {
    const py = view.height - view.offsetY - row * view.cell
    ctx.beginPath()
    ctx.moveTo(view.offsetX, py)
    ctx.lineTo(view.offsetX + view.cols * view.cell, py)
    ctx.stroke()
  }

  const highlightSet = new Set(props.highlight ?? [])
  const gap = Math.max(1, Math.round(view.cell * 0.12))

  // 库位
  visibleLocations.value.forEach((location) => {
    const { cx, cy } = centerOf(location, view)
    const size = view.cell - gap
    const left = cx - size / 2
    const top = cy - size / 2
    const highlighted = highlightSet.has(location.id)

    ctx.fillStyle = STATUS_COLOR[location.status] ?? '#f0f2f5'
    ctx.fillRect(left, top, size, size)
    ctx.strokeStyle = highlighted ? HIGHLIGHT_COLOR : (STATUS_BORDER[location.status] ?? '#dcdfe6')
    ctx.lineWidth = highlighted ? 2.5 : 1
    ctx.strokeRect(left, top, size, size)

    if (props.showCode && view.cell >= 18) {
      ctx.fillStyle = '#303133'
      ctx.font = '10px "Microsoft YaHei", sans-serif'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText(location.code, cx, cy)
    }
  })

  // 拣选路径
  if (props.path && props.path.length > 1) {
    ctx.strokeStyle = PATH_COLOR
    ctx.lineWidth = 2
    ctx.setLineDash([6, 4])
    ctx.beginPath()
    props.path.forEach((point, index) => {
      const { cx, cy } = centerOf(point, view)
      if (index === 0) {
        ctx.moveTo(cx, cy)
      } else {
        ctx.lineTo(cx, cy)
      }
    })
    ctx.stroke()
    ctx.setLineDash([])
    props.path.forEach((point) => {
      const { cx, cy } = centerOf(point, view)
      ctx.fillStyle = PATH_COLOR
      ctx.beginPath()
      ctx.arc(cx, cy, 3, 0, Math.PI * 2)
      ctx.fill()
    })
  }

  // 出库口
  const exit = props.exit ?? { x: 0, y: 0 }
  const { cx: ex, cy: ey } = centerOf(exit, view)
  ctx.fillStyle = '#409eff'
  ctx.beginPath()
  ctx.arc(ex, ey, Math.max(6, view.cell * 0.45), 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = '#fff'
  ctx.font = 'bold 10px "Microsoft YaHei", sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText('出', ex, ey + 1)
}

/**
 * 把鼠标事件换算成库位（用于点击与悬停）。
 *
 * @param event 鼠标事件
 * @returns 命中的库位，未命中返回 null
 */
function hitTest(event: MouseEvent): PlaneLocation | null {
  const canvas = canvasRef.value
  if (!canvas) {
    return null
  }
  const rect = canvas.getBoundingClientRect()
  const px = event.clientX - rect.left
  const py = event.clientY - rect.top
  const view = computeViewport(rect.width)
  const col = Math.floor((px - view.offsetX) / view.cell)
  const row = Math.floor((view.height - view.offsetY - py) / view.cell)
  const x = view.minX + col
  const y = view.minY + row
  return (
    visibleLocations.value.find(
      (item) => item.x === x && item.y === y && (props.layer == null || (item.layer ?? 1) === props.layer),
    ) ?? null
  )
}

function onClick(event: MouseEvent): void {
  const location = hitTest(event)
  if (location) {
    emit('location-click', location)
  }
}

function onMove(event: MouseEvent): void {
  emit('location-hover', hitTest(event))
}

onMounted(() => {
  draw()
  if (typeof ResizeObserver !== 'undefined' && canvasRef.value?.parentElement) {
    observer = new ResizeObserver(() => draw())
    observer.observe(canvasRef.value.parentElement)
  }
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})

watch(
  () => [props.locations, props.highlight, props.path, props.layer, props.cellSize, props.height],
  () => draw(),
  { deep: true },
)

defineExpose({ redraw: draw, availableLayers })
</script>

<template>
  <div class="plane">
    <canvas
      ref="canvasRef"
      class="plane__canvas"
      @click="onClick"
      @mousemove="onMove"
      @mouseleave="emit('location-hover', null)"
    />
    <div class="plane__legend">
      <span><i class="plane__dot plane__dot--free" />空闲</span>
      <span><i class="plane__dot plane__dot--occupied" />占用</span>
      <span><i class="plane__dot plane__dot--disabled" />停用</span>
      <span><i class="plane__dot plane__dot--highlight" />高亮（推荐/本方案）</span>
      <span><i class="plane__dot plane__dot--exit" />出库口</span>
      <span v-if="path.length > 1"><i class="plane__dot plane__dot--path" />拣选路径</span>
    </div>
  </div>
</template>

<style scoped>
.plane {
  width: 100%;
  overflow: auto;
}

.plane__canvas {
  display: block;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  cursor: pointer;
}

.plane__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 10px;
  font-size: 12px;
  color: #606266;
}

.plane__legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.plane__dot {
  width: 12px;
  height: 12px;
  border-radius: 2px;
  display: inline-block;
}

.plane__dot--free {
  background: #e8f3ff;
  border: 1px solid #a0cfff;
}

.plane__dot--occupied {
  background: #67c23a;
  border: 1px solid #529b2e;
}

.plane__dot--disabled {
  background: #c0c4cc;
  border: 1px solid #909399;
}

.plane__dot--highlight {
  background: #fff;
  border: 2px solid #f56c6c;
}

.plane__dot--exit {
  background: #409eff;
  border-radius: 50%;
}

.plane__dot--path {
  background: repeating-linear-gradient(90deg, #e6a23c 0 4px, transparent 4px 7px);
}
</style>
