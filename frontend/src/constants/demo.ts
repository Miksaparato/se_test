import type { SkuBrief } from '@/types'

/**
 * 联调演示用种子数据，与后端 InMemoryWarehouseDataRepository 保持一致。
 * TODO(a): 待 a 提供 SKU 列表接口（API-030）与仓库列表接口后，
 *          改为从后端拉取，删除本文件中的硬编码数据。
 */

/** 演示仓库 id（一号仓）。 */
export const DEMO_WAREHOUSE_ID = 1

/** 演示 SKU 列表（选择「货物」下拉框用）。 */
export const DEMO_SKUS: SkuBrief[] = [
  { id: 1, skuCode: 'SKU-001', name: '高频电子元件', weight: 50, turnoverRate: 0.9, priority: 5, category: '电子' },
  { id: 2, skuCode: 'SKU-002', name: '重型机械', weight: 180, turnoverRate: 0.2, priority: 3, category: '机械' },
  { id: 3, skuCode: 'SKU-003', name: '服装', weight: 8, turnoverRate: 0.6, priority: 2, category: '服装' },
  { id: 4, skuCode: 'SKU-004', name: '食品', weight: 12, turnoverRate: 0.7, priority: 4, category: '食品' },
  { id: 5, skuCode: 'SKU-005', name: '五金配件', weight: 90, turnoverRate: 0.4, priority: 3, category: '机械' },
]
