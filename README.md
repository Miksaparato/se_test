# 小型智能仓储库位分配仿真系统（se_test）

智能仓储库位分配仿真系统，小组作业，三名成员按模块分工开发。

## 目录结构

```
se_test/
├── backend/     # Java 17 + Spring Boot 3.2（Maven）
├── frontend/    # Vue 3 + TypeScript + Vite（Pinia + Vue Router + Element Plus + ECharts）
├── database/    # 数据库相关说明（建表脚本见 backend/sql/schema.sql 与 db/migration）
├── docs/        # 数据库设计说明书 / 鉴权中间件规范 / 使用说明文档 / 汇报与遗留问题清单
└── document/    # 需求 / 分工 / 接口 / 代码规范 / 算法说明（冻结契约）
```

## 分支模型

`main`（稳定）← `dev`（集成）← `feature/a-*` / `feature/b-*` / `feature/c-*`（开发）。

模块归属（《任务分工文档》）：

| 成员 | 模块 | 后端包 | 前端页面 |
| --- | --- | --- | --- |
| **a** | 基础数据 + RBAC | `com.wms.data.*`、`com.wms.security`、`com.wms.domain` | 登录、数据管理、用户/角色 |
| **b** | 库位智能推荐 + 方案对比 | `com.wms.recommend.*` | 入库推荐、方案对比、权重配置 |
| **c** | 入库/出库仿真 + 可视化 | `com.wms.simulation.*` | 总览平面图、仿真配置、路径与统计 |

---

## 一、准备数据库（MySQL 8）

任选其一，**两种方式都受支持**：

**方式一（推荐）：交给 Flyway 自动执行**

```bash
mysql -u root -p -e "CREATE DATABASE wms_sim DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
cd backend && mvn -s maven-settings.xml spring-boot:run
```

**方式二：手工执行 `backend/sql/schema.sql`**（适合数据库评审或无 Maven 环境）

```bash
mysql -u root -p --default-character-set=utf8mb4 < backend/sql/schema.sql
```

> `schema.sql` 由 `backend/tools/build_schema_sql.py` 从 `db/migration/V*.sql` 生成，
> **唯一事实来源是 Flyway 脚本**：改了脚本请重新运行该工具同步。
>
> 手工建过库的环境没有 `flyway_schema_history` 表，应用启动时 Flyway 会以
> `baseline-version=3` 认领既有结构（详见 `docs/数据库设计说明书.md` 6.2.3），
> 不会因为「表已存在」而启动失败，也不会改动任何数据。

默认连接参数（可用环境变量覆盖）：库 `wms_sim`、账号 `root`、密码 `123456`。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `WMS_DB_URL` | `jdbc:mysql://127.0.0.1:3306/wms_sim?...` | JDBC 连接串 |
| `WMS_DB_USERNAME` / `WMS_DB_PASSWORD` | `root` / `123456` | 数据库账号 |
| `WMS_JWT_SECRET` | 开发用固定串 | JWT 签名密钥，生产必须覆盖 |
| `WMS_REPOSITORY` | `mysql` | 数据访问实现：`mysql` / `memory` |
| `WMS_FLYWAY_BASELINE_VERSION` | `3` | Flyway 认领既有库的基线版本，严格模式设为 `0` |

## 二、启动后端

依赖：JDK 17、Maven 3.8+。

```bash
cd backend
mvn -s maven-settings.xml spring-boot:run     # 默认 8081 端口
```

**无 MySQL 也能跑**（离线演示模式，内置 1 仓库 / 3 货架 / 36 库位 / 5 SKU / 3 方案）：

```bash
cd backend
mvn -s maven-settings.xml spring-boot:run -Dspring-boot.run.arguments=--wms.repository=memory
```

运行测试：

```bash
cd backend
mvn -s maven-settings.xml test                 # 单测 + MockMvc（80 项）
mvn -s maven-settings.xml test -Pe2e           # 另跑真实 HTTP 端到端用例
```

## 三、写入演示数据（验收用）

后端起来后执行（通过**公开 REST API** 建数据，会走真实业务校验，可重复执行）：

```powershell
powershell -ExecutionPolicy Bypass -File backend/tools/seed_demo_data.ps1
```

产出：1 个仓库（`WH-01`，出库口在 `(0,0)`）、3 个货架（巷道 A/B/C，各 4 列 × 3 层 = **36 个库位**）、
5 个 SKU、12 张出库订单。RBAC 种子（用户/角色/权限）完全不改动。

> **库位容量与货物尺寸必须同单位**（体积口径）：schema 默认容量是 `100.000`，
> 而接口文档示例的货物尺寸是 `30×20×10 = 6000`。容量太小时，容量约束会把所有库位判为「放不下」。
> 脚本按 `-LocationCapacity`（默认 100000）建库位，并会把容量过小的既有库位一并纠正。

## 四、一键验收自检

按《需求分析文档》第 11 节的 8 条验收标准逐条自检（需后端已启动）：

```powershell
powershell -ExecutionPolicy Bypass -File backend/tools/acceptance.ps1
```

## 五、启动前端

依赖：Node 18+。

```bash
cd frontend
npm install
npm run dev            # 默认 5173，已配置代理到后端 8081
```

质量校验：

```bash
npm run lint           # ESLint
npm run type-check     # vue-tsc（检查 tsconfig.app.json / tsconfig.node.json）
npm run build          # 生产构建
npm run preview        # 静态服务器 4173（/api 代理到 8081，用于联调验证）
npm run verify:integration   # 27 项联调断言（角色菜单显隐 + 越权拦截）
```

## 六、默认账号（初始密码均为 `admin123`）

| 账号 | 角色 | 权限范围 |
| --- | --- | --- |
| `admin` | 系统管理员 | 全部 9 项权限 |
| `operator` | 仓库操作员 | 货物/订单管理、库位推荐、运行仿真、结果查看 |
| `analyst` | 分析人员 | 运行仿真、结果查看、方案对比、报告导出 |
| `viewer` | 只读访客 | 结果查看、方案对比 |

> 首次登录后请通过 API-004（`PUT /api/v1/auth/password`）修改密码；
> 演示/生产部署前务必删除或改密这 4 个内置账号。

## 七、接口与契约

Base URL `/api/v1`，统一响应 `{ "code": 0, "message": "success", "data": {} }`，
认证头 `Authorization: Bearer <token>`。完整清单见 `document/接口文档.md`。

| 开发者 | 接口范围 | 模块 |
| --- | --- | --- |
| a | API-001 ~ API-040 | 认证/RBAC、仓库/货架/库位、SKU、订单、导入导出 |
| b | API-041 ~ API-053 | 推荐引擎、权重/规则配置、方案对比、建议与报告 |
| c | API-054 ~ API-062 | 入库仿真、出库仿真与路程统计、随机订单集 |

## 八、测试与验收结论

| 项目 | 命令 | 结果 |
| --- | --- | --- |
| 后端单元 + 集成 | `cd backend && mvn -s maven-settings.xml test` | 115 / 115 |
| 后端真实 HTTP 端到端 | `mvn -s maven-settings.xml test -Pe2e` | 61 / 61（独立库 `wms_sim_e2e*`） |
| 前端类型检查 / 规范 / 构建 | `npm run type-check && npm run lint && npm run build` | 通过 |
| 前端联调断言 | `npm run preview` + `npm run verify:integration` | 27 / 27 |
| 验收标准 1~8 自检 | `backend/tools/acceptance.ps1` | 47 / 47 |

> `-Pe2e` 的端到端用例各自使用独立数据库（`wms_sim_e2e` / `_wh` / `_rbac` / `_imp` / `_sku`），
> 不会读写你在用的 `wms_sim`。

关键文档：

- `document/智能仓储库位分配仿真系统-需求分析文档.md` —— 需求与验收标准
- `document/接口文档.md` —— REST API 冻结契约（含权限码与实现补充说明）
- `document/代码规范.md` —— 编码/目录/测试规范
- `document/T-6-评分模型算法说明.md` —— 推荐评分模型公式与口径（b）
- `document/T-6-仿真与距离口径说明.md` —— 距离口径、六种策略、约束软硬划分、出库统计（c）
- `docs/数据库设计说明书.md` —— 12 张表的数据字典 + 表结构与代码的一致性核对结论
- `docs/鉴权中间件规范.md` —— `@PreAuthorize` 用法与 401/403 返回格式
- `docs/使用说明文档.md` —— 面向使用者的操作手册（含 b、c 模块操作说明）
- `docs/小组分工与完成情况汇报.md` —— 小组分工、8 条验收标准达成情况、三位成员的个人工作小结
- `docs/后续待解决问题清单.md` —— 遗留问题与改进计划（18 项，含现象证据、建议方案、验收标准与迭代排期）
