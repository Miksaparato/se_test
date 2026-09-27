# 小型智能仓储库位分配仿真系统 — 前端工程说明

> 负责人：a（谢卓成），对应任务 A-F1 ~ A-F4
> 技术栈：Vue 3 + TypeScript + Vite + Pinia + Vue Router + Element Plus（《代码规范》V1.1 第 1、5 节）

---

## 1. 环境与启动

```bash
cd frontend
npm install
npm run dev            # 开发服务器 http://localhost:5173（/api 代理到 8080）
```

其它命令：

| 命令 | 作用 |
| --- | --- |
| `npm run type-check` | `vue-tsc` 类型检查（零错误为通过） |
| `npm run build` | 生产构建，产物在 `dist/` |
| `npm run preview` | 预览 `dist/` 静态资源并代理 `/api`（内置脚本，见下） |
| `npm run verify:integration` | **联调验证脚本**：不依赖浏览器，校验静态资源、代理链路、四角色权限码与越权拦截 |
| `npm run lint` / `npm run format` | ESLint / Prettier |

> `npm run preview` 用的是 `scripts/preview-server.mjs` 而不是 `vite preview`：
> 后者需要 esbuild 派生服务子进程，在受限/沙箱环境下会因进程限制失败；
> 内置脚本只用 Node 能力，等价地提供静态资源 + `/api` 代理。

启动顺序：**先起后端**（`backend: mvn -s maven-settings.xml spring-boot:run`），再起前端。

---

## 2. 演示账号

| 账号 | 角色 | 可见菜单 |
| --- | --- | --- |
| `admin` | 系统管理员 | 全部 8 个菜单 |
| `operator` | 仓库操作员 | 仓库总览 + 基础数据（5 项），**无系统管理** |
| `analyst` | 分析人员 | 同 operator（本步页面范围内） |
| `viewer` | 只读访客 | 菜单可见但**所有写操作按钮不显示** |

初始密码均为 `admin123`。登录页提供一键填充按钮，便于按角色验收。

---

## 3. 目录结构与职责

```
frontend/
├── src/
│   ├── api/                 # axios 封装 + 按模块拆分的接口定义（与《接口文档》API 编号对应）
│   │   ├── http.ts          # 统一实例：注入 Token、401 跳登录、403 提示无权限、文件上传/下载
│   │   ├── auth.ts          # API-001~004
│   │   ├── admin.ts         # API-005~015（用户/角色/权限）
│   │   └── data.ts          # API-016~040（仓库/货架/库位/货物/订单/导入导出）
│   ├── components/common/   # 通用组件（DataTable：分页表格）
│   ├── directives/          # v-permission 按钮级权限指令
│   ├── layout/              # MainLayout：顶部栏 + 按权限过滤的侧边菜单
│   ├── router/              # 路由表 + 全局守卫（meta.permission）
│   ├── stores/              # Pinia：authStore（用户 + 权限缓存）
│   ├── styles/              # 主题变量与全局样式
│   ├── types/               # 与后端 DTO 严格对应的 TS 类型
│   └── views/
│       ├── auth/            # 登录页（A-F1）、个人设置（改密）
│       ├── overview/        # 仓库总览页（数据来自 API-021，平面图待接入 c 的组件）
│       ├── data/            # 仓库/货架/库位/货物/订单管理页（A-F2）
│       ├── admin/           # 用户管理、角色与权限页（A-F3）
│       └── error/           # 403 / 404
└── scripts/                 # 预览服务器与联调验证脚本（不参与打包）
```

---

## 4. 权限显隐的实现（A-F4）

三层配合，**前端只做体验优化，后端强制鉴权才是安全边界**（FR-RBAC-3）：

1. **路由级**：`router/index.ts` 中每条路由声明 `meta.permission`，全局 `beforeEach` 校验；
   未登录跳登录页，越权跳 403 页。
2. **菜单级**：`MainLayout.vue` 按 `meta.permission` 过滤菜单项 —— 不同角色登录后看到的菜单不同。
3. **按钮级**：`v-permission="'user:manage'"` 指令在权限不足时直接移除元素。

接口层同时处理：401 清除登录态并跳登录页；403 提示「无该操作权限」（《鉴权中间件规范》第 6 节）。

权限码全部来自《接口文档》1.4 清单，`src/types/auth.ts` 中的 `PermissionCode` 联合类型会在
编译期拦住拼错的权限码。

---

## 5. 与 c 的平面图组件对接（COM-5）

`views/overview/OverviewView.vue` 已按 **API-021** 取到布局数据（`warehouses/{id}/layout`），
并把库位平铺为 `flatLocations`。待 c 的公共组件就绪后，只需替换该页的占位区：

```html
<!-- 组件 props 契约见《代码规范》5.2 / COM-5 -->
<WarehouseLayout
  :locations="flatLocations"
  :exit="layout.exit"
  :highlight="highlightIds"
  :path="pathPoints"
  @location-click="onLocationClick"
/>
```

平面图组件的实现与样式由 c 负责，a 只消费，不复制、不改其内部（冻结契约）。

---

## 6. 本步已验证的内容

| 验证项 | 方式 | 结果 |
| --- | --- | --- |
| 类型检查 | `npm run type-check` | 0 错误 |
| 生产构建 | `npm run build` | 成功，产物 `dist/`（含各页面按路由懒加载分包） |
| 静态资源 | 联调脚本 | `index.html` 可访问且含 `#app` 挂载点 |
| 代理链路 | 联调脚本 | `/api` → 8080 转发正常，匿名访问返回统一 401 结构 |
| 四角色权限码 | 联调脚本 | 与《接口文档》1.4 权限矩阵逐项一致（**27 项断言全部通过**） |
| 菜单可见性 | 联调脚本 | 按权限码推导：viewer/operator 不可见系统管理；admin 全部可见 |
| 越权拦截 | 联调脚本 | viewer 访问用户列表 → 403/40301；operator 创建仓库 → 403 |
| 有序写入 | 联调脚本 | operator 创建货物成功（`sku:manage`） |
