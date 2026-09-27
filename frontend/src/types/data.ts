/**
 * 基础数据相关类型（《接口文档》第 5~7 节）。
 *
 * 负责人：a
 */

/** 库位状态。 */
export type LocationStatus = 'free' | 'occupied' | 'disabled'

/** 订单状态。 */
export type OrderStatus = 'pending' | 'picking' | 'completed' | 'cancelled'

/** 出库口坐标（曼哈顿距离终点）。 */
export interface ExitPoint {
  x: number
  y: number
  layer?: number
}

/** 仓库（API-016~019）。 */
export interface Warehouse {
  id: number
  code: string
  name: string
  length: number | null
  width: number | null
  height: number | null
  exit: ExitPoint
  rackCount: number
  remark: string | null
  createdAt: string | null
}

/** 仓库创建/更新请求。 */
export interface WarehousePayload {
  code?: string
  name?: string
  length?: number | null
  width?: number | null
  height?: number | null
  exitX?: number | null
  exitY?: number | null
  exitLayer?: number | null
  remark?: string | null
}

/** 货架（API-022~023）。 */
export interface Rack {
  id: number
  warehouseId: number
  code: string
  aisle: string
  columnCount: number
  layerCount: number
  x: number | null
  y: number | null
  orientation: 'row' | 'column'
  locationCount: number
  createdAt: string | null
}

/** 货架创建/更新请求。 */
export interface RackPayload {
  warehouseId?: number
  code?: string
  aisle?: string
  columnCount?: number
  layerCount?: number
  x?: number | null
  y?: number | null
  orientation?: 'row' | 'column'
  /** 仅创建时有效：按列×层批量生成库位 */
  generateLocations?: boolean
  /**
   * 批量生成库位时使用的库位容量（体积口径）。
   *
   * 必须与 `skus.size` 同单位：留空取后端默认 100，若货物尺寸较大（如 30×20×10 = 6000）
   * 会导致容量校验把全部库位判为「放不下」。
   */
  capacity?: number | null
}

/** 库位（API-025~027）。 */
export interface Location {
  id: number
  rackId: number
  warehouseId: number
  code: string
  x: number
  y: number
  layer: number
  status: LocationStatus
  capacity: number
  occupiedSkuId: number | null
  createdAt: string | null
}

/** 库位创建/更新请求。 */
export interface LocationPayload {
  rackId?: number
  code?: string
  x?: number
  y?: number
  layer?: number
  status?: LocationStatus
  capacity?: number | null
  occupiedSkuId?: number | null
}

/** 尺寸（与后端 SkuSize 一致）。 */
export interface SkuSize {
  length: number | null
  width: number | null
  height: number | null
}

/** 货物（API-029~031）。 */
export interface Sku {
  id: number
  skuCode: string
  name: string
  weight: number
  turnoverRate: number
  priority: number
  category: string | null
  size: SkuSize | null
  volume: number
  remark: string | null
  createdAt: string | null
}

/** 货物创建/更新请求。 */
export interface SkuPayload {
  skuCode?: string
  name?: string
  weight?: number
  turnoverRate?: number
  priority?: number
  category?: string | null
  size?: SkuSize | null
  remark?: string | null
}

/** 订单（API-033~035）。 */
export interface Order {
  id: number
  orderNo: string
  skuId: number
  skuCode: string | null
  quantity: number
  priority: number
  placedAt: string | null
  status: OrderStatus
  remark: string | null
  createdAt: string | null
}

/** 订单创建/更新请求。 */
export interface OrderPayload {
  orderNo?: string | null
  skuId?: number
  quantity?: number
  priority?: number
  placedAt?: string | null
  status?: OrderStatus
  remark?: string | null
}

/** 平面布局中的库位节点（API-021，字段与《接口文档》示例一致）。 */
export interface LayoutLocation {
  id: number
  code: string
  x: number
  y: number
  layer: number
  status: LocationStatus
  capacity: number
}

/** 平面布局中的货架节点。 */
export interface LayoutRack {
  rackId: number
  code: string
  aisle: string
  columnCount: number
  layerCount: number
  x: number | null
  y: number | null
  orientation: 'row' | 'column'
  locations: LayoutLocation[]
}

/** 仓库平面布局（API-021，供 c 的平面图组件消费）。 */
export interface WarehouseLayout {
  warehouseId: number
  code: string
  name: string
  length: number | null
  width: number | null
  height: number | null
  exit: ExitPoint
  racks: LayoutRack[]
}

/** 导入结果（API-037/038）。 */
export interface ImportResult {
  total: number
  inserted: number
  updated: number
  skipped: number
  failed: number
  errors: Array<{ line: number; column: string | null; message: string }>
  truncated: boolean
}
