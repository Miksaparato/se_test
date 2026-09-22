# 小型智能仓储库位分配仿真系统（se_test）

智能仓储库位分配仿真系统，小组作业，三名成员按模块分工开发。

## 目录结构

```
se_test/
├── backend/     # Java 17 + Spring Boot 3.2（Maven）
├── frontend/    # Vue 3 + TypeScript + Vite（Pinia + Vue Router + ECharts）
├── database/    # MySQL 8 + Flyway 迁移脚本（成员 a 牵头）
└── document/    # 需求 / 分工 / 接口 / 代码规范 / 算法说明
```

## 分支模型

`main`（稳定）← `dev`（集成）← `feature/a-*` / `feature/b-*` / `feature/c-*`（开发）。

当前各模块进展：

- **成员 a**：基础数据 + RBAC（`feature_a`）
- **成员 b**：库位智能推荐 / 方案对比 / 报告导出 / 调参（`feature_b`）
- **成员 c**：入库策略仿真 / 出库仿真 / 可视化（`feature_c`）

## 后端启动（成员 b 模块，可独立运行）

依赖：JDK 17、Maven 3.8+。

```bash
cd backend
mvn spring-boot:run          # 默认 8080 端口
```

后端 b 模块目前使用内存数据实现（`InMemoryWarehouseDataRepository`），内置演示数据（1 个仓库、36 个库位、5 个 SKU、3 个分配方案），无需数据库即可启动。联调时由成员 a 以 MyBatis-Plus 实现替换。

运行单测：

```bash
cd backend
mvn test
```

## 前端启动（成员 b 模块页面）

依赖：Node 18+。

```bash
cd frontend
npm install
npm run dev                  # 默认 5173，已配置代理到后端 8080
```

页面：`/recommend` 入库推荐、`/compare` 方案对比、`/config` 权重与规则配置。

## 成员 b 接口一览（API-041 ~ API-053）

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| API-041 | POST | `/api/v1/recommendations` | 计算推荐 |
| API-042 | GET | `/api/v1/recommendations/{id}` | 查询推荐 |
| API-043 | POST | `/api/v1/recommendations/{id}/adopt` | 采用推荐 |
| API-044/045 | GET/PUT | `/api/v1/config/weights` | 权重配置 |
| API-046/047 | GET/PUT | `/api/v1/config/rules` | 分层规则 |
| API-048 | POST | `/api/v1/config/calibrate` | 参数校准 |
| API-049~053 | GET/POST | `/api/v1/plans[...]` | 方案对比 / 建议 / 报告 |

详见 `document/接口文档.md` 与 `document/T-6-评分模型算法说明.md`。
