<script setup lang="ts">
/**
 * 通用分页表格组件：统一分页条、加载态与空数据提示。
 *
 * 目的：仓库/货架/库位/货物/订单/用户/角色 七个列表页共用同一套分页与样式，
 * 避免各页重复实现（《代码规范》5.1 组件化）。
 *
 * 负责人：a
 */
const props = withDefaults(
  defineProps<{
    /** 数据行 */
    rows: unknown[]
    /** 总记录数 */
    total: number
    /** 当前页 */
    page: number
    /** 每页条数 */
    pageSize: number
    /** 加载状态 */
    loading?: boolean
    /** 空数据提示 */
    emptyText?: string
  }>(),
  {
    loading: false,
    emptyText: '暂无数据',
  },
)

const emit = defineEmits<{
  /** 页码变化 */
  (event: 'update:page', value: number): void
  /** 每页条数变化 */
  (event: 'update:pageSize', value: number): void
}>()

/**
 * 页码变化。
 *
 * @param value 新页码
 */
function handlePageChange(value: number): void {
  emit('update:page', value)
}

/**
 * 每页条数变化。
 *
 * @param value 新条数
 */
function handleSizeChange(value: number): void {
  emit('update:pageSize', value)
}
</script>

<template>
  <div>
    <el-table v-loading="props.loading" :data="props.rows" border stripe size="small">
      <slot />
      <template #empty>
        <span class="wms-muted">{{ props.emptyText }}</span>
      </template>
    </el-table>

    <div class="wms-pagination">
      <el-pagination
        :current-page="props.page"
        :page-size="props.pageSize"
        :total="props.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>
