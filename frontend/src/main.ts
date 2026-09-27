import { createPinia } from 'pinia'
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import 'element-plus/dist/index.css'
import '@/styles/main.css'

import App from './App.vue'
import { setupPermissionDirective } from '@/directives/permission'
import router from './router'

/**
 * 前端入口（A-F1~A-F4）。
 *
 * 负责人：a
 */
const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 注册 Element Plus 图标，供菜单图标名动态使用
Object.entries(ElementPlusIconsVue).forEach(([name, component]) => {
  app.component(name, component)
})

setupPermissionDirective(app)

app.mount('#app')
