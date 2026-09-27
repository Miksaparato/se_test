<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { getWarehouseLayout, listWarehouses } from '@/api/data'
import WarehouseLayout from '@/components/warehouse/WarehouseLayout.vue'
import type { LayoutLocation, Warehouse, WarehouseLayout as WarehouseLayoutData } from '@/types'

/**
 * 仓库总览页（界面需求 8.2 / C-F2）。
 *
 * 页面职责只有「取数与展示」：平面图渲染完全交给 c 的公共组件
 * {@link WarehouseLayout}（C-F1，冻结契约 COM-5），本页不复制任何绘制逻辑。
 *
 * 负责人：a（页面骨架与数据）、c（平面图组件）
 */
const loading = ref(false)
const warehouses = ref<Warehouse[]>([])
const layout = ref<WarehouseLayoutData | null>(null)
const selectedLayer = ref<number | null>(null)
const activeLocation = ref<LayoutLocation | null>(null)

const query = reactive({
  warehouseId: undefined as number | undefined,
})

/** 布局中的库位平铺列表，供平面图组件消费（props: locations）。 */
const flatLocations = computed<LayoutLocation[]>(() =>
  (layout.value?.racks ?? []).flatMap((rack) => rack.locations),
)

/** 层号可选值。 */
const layers = computed(() => {
  const set = new Set<number>()
  flatLocations.value.forEach((item) => set.add(item.layer))
  return [...set].sort((a, b) => a - b)
})

/** 库位状态统计。 */
const statusSummary = computed(() => {
  const summary = { free: 0, occupied: 0, disabled: 0 }
  flatLocations.value.forEach((location) => {
    if (location.status === 'free') summary.free += 1
    else if (location.status === 'occupied') summary.occupied += 1
    else summary.disabled += 1
  })
  return summary
})

/** 加载仓库下拉列表。 */
async function loadWarehouses(): Promise<void> {
  const result = await listWarehouses({ page: 1, page_size: 100 })
  warehouses.value = result.list
  if (!query.warehouseId && result.list.length > 0) {
    query.warehouseId = result.list[0]?.id
  }
}

/** 加载并展示所选仓库的布局。 */
async function loadLayout(): Promise<void> {
  activeLocation.value = null
  if (!query.warehouseId) {
    layout.value = null
    return
  }
  loading.value = true
  try {
    layout.value = await getWarehouseLayout(query.warehouseId)
    selectedLayer.value = layers.value[0] ?? null
  } catch {
    layout.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 统计某货架下指定状态的库位数。
 *
 * 放在脚本侧而不是模板内联：模板表达式里写 TS 类型标注会被模板编译器当作 JS 解析。
 *
 * @param locations 货架下的库位列表
 * @param status 目标状态（free / occupied / disabled）
 */
function countByStatus(
  locations: Array<{ status: string }> | undefined,
  status: string,
): number {
  return (locations ?? []).filter((location) => location.status === status).length
}

/**
 * 点击平面图库位，展示详情。
 *
 * @param location 被点击的库位
 */
function onLocationClick(location: LayoutLocation): void {
  activeLocation.value = location
}

onMounted(async () => {
  await loadWarehouses()
  if (warehouses.value.length === 0) {
    ElMessage.info('尚未创建仓库，请先到「仓库管理」新建仓库与库位')
    return
  }
  await loadLayout()
})
</script>

<template>
  <div class="wms-page">
    <el-card shadow="never">
      <div class="wms-search-bar">
        <span>选择仓库：</span>
        <el-select
          v-model="query.warehouseId"
          placeholder="请选择仓库"
          style="width: 240px"
          @change="loadLayout"
        >
          <el-option
            v-for="item in warehouses"
            :key="item.id"
            :label="`${item.name}（${item.code}）`"
            :value="item.id"
          />
        </el-select>
        <span>楼层：</span>
        <el-select
          v-model="selectedLayer"
          placeholder="全部层"
          clearable
          style="width: 140px"
        >
          <el-option v-for="l in layers" :key="l" :label="`第 ${l} 层`" :value="l" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadLayout">刷新布局</el-button>
      </div>

      <template v-if="layout">
        <el-descriptions :column="4" border size="small">
          <el-descriptions-item label="仓库">
            {{ layout.name }}（{{ layout.code }}）
          </el-descriptions-item>
          <el-descriptions-item label="尺寸">
            {{ layout.length ?? '-' }} × {{ layout.width ?? '-' }} × {{ layout.height ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="出库口">
            ({{ layout.exit.x }}, {{ layout.exit.y }})
          </el-descriptions-item>
          <el-descriptions-item label="货架数">{{ layout.racks.length }}</el-descriptions-item>
          <el-descriptions-item label="库位总数">{{ flatLocations.length }}</el-descriptions-item>
          <el-descriptions-item label="空闲">
            <el-tag type="success" size="small">{{ statusSummary.free }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="占用">
            <el-tag type="warning" size="small">{{ statusSummary.occupied }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="停用">
            <el-tag type="info" size="small">{{ statusSummary.disabled }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">仓库平面图（C-F1 公共组件）</el-divider>

        <el-row :gutter="16">
          <el-col :span="18">
            <WarehouseLayout
              :locations="flatLocations"
              :exit="layout.exit"
              :layer="selectedLayer"
              :height="440"
              @location-click="onLocationClick"
            />
          </el-col>
          <el-col :span="6">
            <el-card shadow="never" class="overview__detail">
              <template #header>库位详情</template>
              <el-empty v-if="!activeLocation" description="点击平面图上的库位查看详情" :image-size="60" />
              <el-descriptions v-else :column="1" border size="small">
                <el-descriptions-item label="编码">{{ activeLocation.code }}</el-descriptions-item>
                <el-descriptions-item label="坐标">
                  ({{ activeLocation.x }}, {{ activeLocation.y }})
                </el-descriptions-item>
                <el-descriptions-item label="层号">第 {{ activeLocation.layer }} 层</el-descriptions-item>
                <el-descriptions-item label="状态">
                  <el-tag
                    size="small"
                    :type="activeLocation.status === 'free' ? 'success' : activeLocation.status === 'occupied' ? 'warning' : 'info'"
                  >
                    {{ activeLocation.status }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="容量">{{ activeLocation.capacity }}</el-descriptions-item>
                <el-descriptions-item label="距出库口">
                  {{ Math.abs(activeLocation.x - layout.exit.x) + Math.abs(activeLocation.y - layout.exit.y) }}
                  （曼哈顿）
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
        </el-row>

        <el-divider content-position="left">货架明细</el-divider>

        <el-table :data="layout.racks" border stripe size="small">
          <el-table-column prop="rackId" label="货架 id" width="90" />
          <el-table-column prop="code" label="货架编码" width="110" />
          <el-table-column prop="aisle" label="巷道" width="80" />
          <el-table-column label="列 × 层" width="100">
            <template #default="{ row }">{{ row.columnCount }} × {{ row.layerCount }}</template>
          </el-table-column>
          <el-table-column label="基准坐标" width="110">
            <template #default="{ row }">({{ row.x ?? 0 }}, {{ row.y ?? 0 }})</template>
          </el-table-column>
          <el-table-column prop="orientation" label="排布方向" width="100" />
          <el-table-column label="库位数" width="90">
            <template #default="{ row }">{{ row.locations.length }}</template>
          </el-table-column>
          <el-table-column label="状态分布">
            <template #default="{ row }">
              <el-tag type="success" size="small">
                空闲 {{ countByStatus(row.locations, 'free') }}
              </el-tag>
              <el-tag type="warning" size="small" class="overview__tag">
                占用 {{ countByStatus(row.locations, 'occupied') }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <el-empty v-else description="暂无布局数据" />
    </el-card>
  </div>
</template>

<style scoped>
.overview__tag {
  margin-left: 6px;
}

.overview__detail {
  height: 440px;
  overflow-y: auto;
}
</style>
