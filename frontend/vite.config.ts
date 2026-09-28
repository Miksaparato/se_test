import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

/**
 * Vite 配置。
 *
 * 负责人：a（A-F1~A-F4 / COM-1）
 * 说明：开发环境把 /api 代理到后端，避免跨域；生产由网关或同域部署处理。
 *      **代理端口必须与后端 `server.port` 一致**（现为 8081，见
 *      `backend/src/main/resources/application.yml`）；改端口时两处要一起改，
 *      否则前端所有请求都会失败（Network 面板表现为 ERR_CONNECTION_REFUSED）。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1500,
  },
})
