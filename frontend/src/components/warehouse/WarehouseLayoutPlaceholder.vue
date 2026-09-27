<script setup lang="ts">
/**
 * 平面图高亮占位组件（B-F2 联调占位）。
 *
 * TODO(c): 待 c 提供 C-F1 平面图公共组件 <WarehouseLayout> 后，替换本组件；
 *          契约 props 见《代码规范》5.2（locations/exit/highlight/path）。
 *          本占位仅按 frozen props 的 highlight 做最简展示，不实现任何绘制逻辑。
 */

defineProps<{
  /** 库位集合（b 的推荐响应不含坐标，联调时为空，待 a/c 提供）。 */
  locations?: unknown[]
  /** 出库口坐标。 */
  exit?: { x: number; y: number }
  /** 高亮库位 id 列表。 */
  highlight?: number[]
}>()
</script>

<template>
  <div class="placeholder">
    <div>平面图高亮区域（待 c 的 C-F1 平面图组件接入）</div>
    <div class="muted" style="margin-top: 8px">
      <span v-if="exit">出库口 ({{ exit.x }}, {{ exit.y }}) · </span>
      高亮库位：{{ (highlight ?? []).join(', ') || '无' }}
    </div>
  </div>
</template>
