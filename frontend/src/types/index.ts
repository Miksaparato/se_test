/**
 * 前端类型统一出口（按模块分文件，禁止一个文件里混放所有人的类型）。
 *
 * 负责人：a（出口与规范）、b（recommend.ts）、c（simulation.ts）
 *
 * | 文件 | 归属 | 覆盖接口 |
 * | --- | --- | --- |
 * | `api.ts` | a | 统一响应/分页结构 |
 * | `auth.ts` | a | API-001 ~ API-015 |
 * | `data.ts` | a | API-016 ~ API-040 |
 * | `recommend.ts` | b | API-041 ~ API-053 |
 * | `simulation.ts` | c | API-054 ~ API-062 |
 */
export * from './api'
export * from './auth'
export * from './data'
export * from './recommend'
export * from './simulation'
