/// <reference types="vite/client" />

/**
 * 环境变量类型声明（Vite）。
 *
 * 负责人：a
 */
interface ImportMetaEnv {
  /** 后端接口基地址；默认空表示走 Vite 代理（/api → 8080） */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/** 单文件组件模块声明，供 vue-tsc 识别 .vue 导入 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
