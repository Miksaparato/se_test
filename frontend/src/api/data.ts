import { del, download, get, post, put, upload } from './http'
import type { PageResult } from '@/types/api'
import type {
  ImportResult,
  Location,
  LocationPayload,
  Order,
  OrderPayload,
  Rack,
  RackPayload,
  Sku,
  SkuPayload,
  Warehouse,
  WarehouseLayout,
  WarehousePayload,
} from '@/types/data'

/**
 * 基础数据接口（API-016~040）。
 *
 * 负责人：a（A-F2）
 */

// ---------------------------------------------------------------- 仓库

/** API-016 创建仓库。 */
export function createWarehouse(payload: WarehousePayload): Promise<Warehouse> {
  return post<Warehouse>('/warehouses', payload)
}

/** API-017 仓库列表（分页）。 */
export function listWarehouses(query: {
  page?: number
  page_size?: number
  keyword?: string
}): Promise<PageResult<Warehouse>> {
  return get<PageResult<Warehouse>>('/warehouses', query as Record<string, unknown>)
}

/** API-018 仓库详情。 */
export function getWarehouse(id: number): Promise<Warehouse> {
  return get<Warehouse>(`/warehouses/${id}`)
}

/** API-019 更新仓库。 */
export function updateWarehouse(id: number, payload: WarehousePayload): Promise<Warehouse> {
  return put<Warehouse>(`/warehouses/${id}`, payload)
}

/** API-020 删除仓库。 */
export function deleteWarehouse(id: number): Promise<void> {
  return del<void>(`/warehouses/${id}`)
}

/**
 * API-021 仓库平面布局。
 *
 * 返回结构即 c 的平面图组件（C-F1）的输入契约。
 */
export function getWarehouseLayout(id: number): Promise<WarehouseLayout> {
  return get<WarehouseLayout>(`/warehouses/${id}/layout`)
}

// ---------------------------------------------------------------- 货架

/** API-022 创建货架（generateLocations=true 时批量生成库位）。 */
export function createRack(payload: RackPayload): Promise<Rack> {
  return post<Rack>('/racks', payload)
}

/** API-023 更新货架。 */
export function updateRack(id: number, payload: RackPayload): Promise<Rack> {
  return put<Rack>(`/racks/${id}`, payload)
}

/** API-024 删除货架。 */
export function deleteRack(id: number): Promise<void> {
  return del<void>(`/racks/${id}`)
}

// ---------------------------------------------------------------- 库位

/** API-025 创建库位。 */
export function createLocation(payload: LocationPayload): Promise<Location> {
  return post<Location>('/locations', payload)
}

/** API-026 更新库位。 */
export function updateLocation(id: number, payload: LocationPayload): Promise<Location> {
  return put<Location>(`/locations/${id}`, payload)
}

/** API-027 库位列表（分页，可按仓库/货架/状态/层过滤）。 */
export function listLocations(query: {
  page?: number
  page_size?: number
  warehouse_id?: number
  rack_id?: number
  status?: string
  layer?: number
}): Promise<PageResult<Location>> {
  return get<PageResult<Location>>('/locations', query as Record<string, unknown>)
}

/** API-028 删除库位。 */
export function deleteLocation(id: number): Promise<void> {
  return del<void>(`/locations/${id}`)
}

// ---------------------------------------------------------------- 货物

/** API-029 货物列表（分页）。 */
export function listSkus(query: {
  page?: number
  page_size?: number
  category?: string
  keyword?: string
}): Promise<PageResult<Sku>> {
  return get<PageResult<Sku>>('/skus', query as Record<string, unknown>)
}

/** API-030 创建货物。 */
export function createSku(payload: SkuPayload): Promise<Sku> {
  return post<Sku>('/skus', payload)
}

/** API-031 更新货物。 */
export function updateSku(id: number, payload: SkuPayload): Promise<Sku> {
  return put<Sku>(`/skus/${id}`, payload)
}

/** API-032 删除货物。 */
export function deleteSku(id: number): Promise<void> {
  return del<void>(`/skus/${id}`)
}

// ---------------------------------------------------------------- 订单

/** API-033 订单列表（分页，默认按优先级降序 + 下达时间升序）。 */
export function listOrders(query: {
  page?: number
  page_size?: number
  status?: string
  sku_id?: number
  order_no?: string
}): Promise<PageResult<Order>> {
  return get<PageResult<Order>>('/orders', query as Record<string, unknown>)
}

/** API-034 创建订单。 */
export function createOrder(payload: OrderPayload): Promise<Order> {
  return post<Order>('/orders', payload)
}

/** API-035 更新订单。 */
export function updateOrder(id: number, payload: OrderPayload): Promise<Order> {
  return put<Order>(`/orders/${id}`, payload)
}

/** API-036 删除订单。 */
export function deleteOrder(id: number): Promise<void> {
  return del<void>(`/orders/${id}`)
}

// ---------------------------------------------------------------- 导入导出

/** API-037 批量导入 SKU。 */
export function importSkus(file: File, strategy?: 'skip' | 'update'): Promise<ImportResult> {
  return upload<ImportResult>('/import/skus', file, strategy ? { strategy } : undefined)
}

/** API-038 批量导入订单。 */
export function importOrders(file: File, strategy?: 'skip' | 'update'): Promise<ImportResult> {
  return upload<ImportResult>('/import/orders', file, strategy ? { strategy } : undefined)
}

/** API-039 导出 SKU（CSV/JSON）。 */
export function exportSkus(format: 'csv' | 'json' = 'csv'): Promise<void> {
  return download('/export/skus', `skus.${format}`, { format })
}

/** API-040 导出订单（CSV/JSON）。 */
export function exportOrders(format: 'csv' | 'json' = 'csv'): Promise<void> {
  return download('/export/orders', `orders.${format}`, { format })
}
