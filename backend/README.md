# 小型智能仓储库位分配仿真系统 — 后端工程说明

> 负责人：a（谢卓成），对应《任务分工文档》COM-1（技术选型与项目脚手架）
> 技术栈：Java 17 + Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + MySQL 8 + Flyway（《代码规范》V1.1 第 1 节）

---

## 1. 环境依赖

| 依赖 | 版本要求 | 说明 |
| --- | --- | --- |
| JDK | **17 或以上** | Spring Boot 3.2 **强制要求 JDK 17+**；JDK 8 无法编译运行 |
| Maven | 3.6+ | 本仓库提供 `backend/maven-settings.xml`，无需改全局配置 |
| MySQL | 8.0+ | 需 `utf8mb4`；Flyway 会自动建表与写入种子数据 |

检查环境：

```bash
java -version      # 必须显示 17 及以上
mvn -v             # Maven 使用的 JDK 也必须为 17+（mvn -v 中的 Java version）
```

> ⚠ `mvn -v` 显示的 Java 版本取决于 `JAVA_HOME`。若 `java -version` 是 17 而 `mvn -v` 仍是 1.8，
> 请把 `JAVA_HOME` 指向 JDK 17 目录后重开终端。

---

## 2. 数据库准备

```sql
CREATE DATABASE wms_sim DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

表结构与种子数据**不需要手工执行**：应用启动时 Flyway 会自动按顺序执行
`src/main/resources/db/migration/` 下的脚本：

| 脚本 | 内容 |
| --- | --- |
| `V1__init_base_data.sql` | `warehouses`、`skus`、`racks`、`locations`、`orders`、`plans` |
| `V2__init_rbac.sql` | `users`、`roles`、`permissions`、`user_roles`、`role_permissions`、`audit_logs` |
| `V3__seed_rbac.sql` | 9 条权限、4 个内置角色、19 条角色权限映射、4 个默认账号 |

### 2.1 默认账号（初始密码均为 `admin123`）

| 账号 | 角色 | 可访问范围 |
| --- | --- | --- |
| `admin` | 系统管理员 | 全部功能 |
| `operator` | 仓库操作员 | 货物/订单管理、库位推荐、入库仿真、结果查看 |
| `analyst` | 分析人员 | 运行仿真、结果查看、方案对比、报告导出 |
| `viewer` | 只读访客 | 仅查看仿真结果与方案对比 |

> 首次登录后请通过 `PUT /api/v1/auth/password`（API-004）修改密码。
> 演示/生产部署前请删除 `operator`、`analyst`、`viewer` 三个验收测试账号或改密。

---

## 3. 配置覆盖

`src/main/resources/application.yml` 为本地开发默认值，均可用环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `WMS_DB_URL` | `jdbc:mysql://127.0.0.1:3306/wms_sim?...` | 数据源 URL（时区 UTC） |
| `WMS_DB_USERNAME` | `root` | 数据库账号 |
| `WMS_DB_PASSWORD` | `123456` | 数据库口令 |
| `WMS_JWT_SECRET` | 本地开发密钥 | **生产必须覆盖**，HS256 要求 ≥ 32 字节 |

---

## 4. 构建与启动

本机 Maven 全局配置存在两个问题（本地仓库路径指向不存在的目录、aliyun 镜像不可达），
因此统一使用仓库内的 `maven-settings.xml`（本地仓库固定为 `backend/.mvn-repo/`，镜像为 Maven Central HTTPS）：

```bash
cd backend

# 编译
mvn -s maven-settings.xml clean compile

# 运行单元测试与集成测试
mvn -s maven-settings.xml test

# 真实 HTTP 端到端测试（随机端口启动真实服务，验证 401/403/404 鉴权链路）
mvn -s maven-settings.xml test -Pe2e

# 启动服务（默认 8081）
mvn -s maven-settings.xml spring-boot:run

# 打包
mvn -s maven-settings.xml clean package
java -jar target/wms-sim-backend.jar
```

启动成功后自检：

```bash
curl -i http://127.0.0.1:8081/api/v1/health
```

- 未携带 Token 时应返回 **401** 与统一响应体（鉴权中间件生效，见 A-B8）；
- 携带合法 `Authorization: Bearer <token>` 时应返回 `{"code":0,"message":"success","data":{"status":"UP",...}}`。

---

## 5. 目录结构

```
backend/
├── pom.xml                     # Maven 依赖（Spring Boot 3.2.5 父 POM）
├── maven-settings.xml          # 工程级 Maven 配置（见第 4 节）
├── tools/                      # 开发辅助脚本（不参与打包）
│   ├── validate_sql.py         # Flyway 脚本静态校验
│   └── verify_seed_password.py # 种子账号 BCrypt 口令校验
└── src/
    ├── main/
    │   ├── java/com/wms/
    │   │   ├── WmsApplication.java           # 启动类
    │   │   ├── common/                       # 统一响应/错误码/异常/分页（COM-1）
    │   │   ├── config/                       # 全局配置（MyBatis-Plus 分页插件）
    │   │   ├── security/                     # 鉴权（a，A-B8）—— 第 3 步
    │   │   ├── domain/{entity,mapper}/       # 实体与 Mapper（a 牵头，COM-2）
    │   │   ├── data/                         # a：基础数据 + RBAC
    │   │   ├── recommend/                    # b：推荐 + 对比
    │   │   └── simulation/                   # c：仿真 + 统计
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/V1~V3*.sql       # Flyway 版本化脚本
    └── test/java/com/wms/                    # 单元测试（T-3）
```

---

## 6. 统一响应与错误码约定

成功与失败均返回（《接口文档》1.2）：

```json
{ "code": 0, "message": "success", "data": {} }
```

| 错误码段 | HTTP | 含义 | 示例 |
| --- | --- | --- | --- |
| 400xx | 400 | 参数校验失败 | 40001 参数校验失败 |
| 401xx | 401 | 未认证 | 40101 未登录或登录已失效 |
| 403xx | 403 | 越权 | 40301 无该操作权限 |
| 404xx | 404 | 资源不存在 | 40404 库位不存在 |
| 422xx | 422 | 业务规则失败 | 42201 库位已占用、42202 重货层高违规 |
| 500xx | 500 | 系统异常 | 50001 数据库操作异常 |

完整清单见 `com.wms.common.ErrorCode`。

---

## 7. 数据导入导出（A-B4 / NFR-7）

### 7.1 导入模板（API-037 / API-038）

接口为 `multipart/form-data`，字段名 `file`，支持 `.csv` / `.xlsx` / `.xls`；可选参数 `strategy`：

| strategy | 含义 |
| --- | --- |
| `skip`（默认） | 编码已存在 → 跳过，计入 `skipped` |
| `update` | 编码已存在 → 覆盖更新，计入 `updated` |

**SKU 导入表头**（列顺序可变，按列名取值）：

```
skuCode,name,weight,turnoverRate,priority,category,length,width,height,remark
SKU-001,高频电子元件,50,0.9,5,电子,30,20,10,
```

| 列 | 必填 | 说明 |
| --- | :-: | --- |
| `skuCode` | ✔ | 唯一；文件内重复会在后一行报错 |
| `name` | ✔ | 货物名称 |
| `weight` | ✔ | 重量(kg)，≥ 0 |
| `turnoverRate` | ✔ | 周转频次，≥ 0 |
| `priority` | ✔ | 出库优先级，1~5 |
| `category` | ✘ | 品类 |
| `length` / `width` / `height` | ✘ | **要么三列都填，要么都不填** |
| `remark` | ✘ | 备注 |

**订单导入表头**（用 `skuCode` 而不是 id，便于跨环境迁移）：

```
orderNo,skuCode,quantity,priority,placedAt,status,remark
SO-20260910-001,SKU-001,20,4,2026-09-10T09:00:00,pending,
```

| 列 | 必填 | 说明 |
| --- | :-: | --- |
| `orderNo` | ✔ | 唯一；文件内重复会在后一行报错 |
| `skuCode` | ✔ | 必须已存在于 `skus` 表 |
| `quantity` | ✔ | ≥ 1 |
| `priority` | ✔ | 1~5 |
| `placedAt` | ✘ | 支持 `yyyy-MM-dd`、`yyyy-MM-dd HH:mm[:ss]`、`yyyy-MM-ddTHH:mm[:ss]`；留空取当前时间 |
| `status` | ✘ | 默认 `pending`；可选 `pending`/`picking`/`completed`/`cancelled` |
| `remark` | ✘ | 备注 |

**导入行为**：合法行入库，非法行**不阻断整体**，响应逐行给出行号与原因：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 5, "inserted": 2, "updated": 0, "skipped": 0, "failed": 3,
    "errors": [
      { "line": 3, "column": "weight", "message": "weight 不是合法数字：abc" },
      { "line": 5, "column": "priority", "message": "出库优先级须为 1~5" }
    ],
    "truncated": false
  }
}
```

> `line` 为文件中的**物理行号**（含表头，与 Excel 显示一致）；错误明细最多返回 100 条，超出时 `truncated = true`。

### 7.2 导出（API-039 / API-040）

- 路径：`GET /api/v1/export/skus`、`GET /api/v1/export/orders`
- 参数：`format=csv`（默认）或 `format=json`
- 响应为**文件流**（非统一 JSON 结构，《接口文档》1.1 已注明"导入导出除外"），带
  `Content-Disposition: attachment; filename=skus-yyyyMMddHHmmss.csv`
- CSV 特性：**UTF-8 BOM**（Excel 打开中文不乱码）、CRLF 行尾、RFC 4180 转义（含逗号/引号的字段会加引号）
- JSON 字段名与接口一致（`skuCode` / `turnoverRate` / `size` / `orderNo` / `placedAt`）

> 导出的 CSV 可直接作为导入模板使用——导出内容能被导入接口原样读回（已用测试固化）。

---

## 8. 测试策略与测试清单

### 8.1 两类测试

| 类别 | 命令 | 数量 | 说明 |
| --- | --- | :-: | --- |
| 单元 + 集成 | `mvn -s maven-settings.xml test` | 63 | 纯 JUnit（不启动 Spring）+ 手工装配上下文的鉴权集成测试；秒级完成，无外部依赖 |
| 真实 HTTP 端到端 | `mvn -s maven-settings.xml test -Pe2e` | 61 | 程序化启动真实应用（真实 MySQL + Flyway + Spring Security），用 HTTP 断言 |

e2e 测试标记为 `@Tag("e2e")`，默认由 surefire 的 `excludedGroups` 排除，需用 `-Pe2e` 显式启用。
每个 e2e 测试使用独立数据库（如 `wms_sim_e2e_rbac`），互不污染。

### 8.2 为什么不用 Mockito

本项目**刻意不使用 Mockito**，原因有二：

1. **受限环境不可用**：Mockito 的内联 mock maker 需要自附加 JVM agent，
   在禁止派生外部进程的环境（沙箱/CI 受限容器）中会直接抛
   `Could not self-attach to current VM using external process`；
   既有的 `net.bytebuddy.experimental=true` 也无法绕过。
2. **必要性不高**：真实对象 + 真实数据库 + 真实 HTTP 的测试更强，且能覆盖
   「BCrypt 校验种子口令」「MyBatis-Plus JSON typeHandler 往返」这类只有真实链路才能暴露的问题。

因此**业务规则被抽成纯函数类**以便无 mock 单测，例如：

| 纯函数类 | 覆盖内容 |
| --- | --- |
| `com.wms.data.location.LocationRules` | 库位状态与占用货物一致性、层号上限、释放货物语义、可删除判定 |
| `com.wms.data.importexport.CsvTabularParser` | RFC 4180 转义、BOM、空行、缺表头 |
| `com.wms.common.ErrorCode` / `ApiResponse` / `PageResult` | 错误码→HTTP 映射、统一响应结构、分页参数规整 |

`@SpringBootTest` 同样不使用：它会无条件注册 Mockito 上下文定制器。
需要真实 Spring 上下文时改为手工装配（见 `SecurityMiddlewareTest`），
需要真实 HTTP 时改为程序化启动（见 `SecurityE2eTest`）。

### 8.3 测试清单

| 测试类 | 数量 | 类型 | 覆盖 |
| --- | :-: | --- | --- |
| `ApiResponseTest` | 9 | 单元 | 统一响应结构、错误码映射、分页规整 |
| `GlobalExceptionHandlerTest` | 13 | 单元 | 13 类异常 → 错误码 → HTTP 状态码，含不外泄堆栈 |
| `JwtTokenProviderTest` | 9 | 单元 | 签发/解析/过期/伪造/弱密钥拒绝 |
| `CsvTabularParserTest` | 10 | 单元 | CSV 解析边界与文件类型调度 |
| `LocationRulesTest` | 12 | 单元 | 库位业务规则（状态一致性、层号、释放语义） |
| `SecurityMiddlewareTest` | 10 | 集成 | 401/403/伪造 Token/权限码精确匹配/JSON 响应体 |
| `SecurityE2eTest` | 6 | e2e | 真实 HTTP 的 401、放行、403、404、伪 Token |
| `AuthE2eTest` | 10 | e2e | API-001~005、四账号权限矩阵、改密、审计日志 |
| `RbacE2eTest` | 15 | e2e | API-006~015、内置角色保护、改权→重登生效闭环 |
| `WarehouseE2eTest` | 9 | e2e | API-016~028、库位批量生成规则、API-021 布局契约 |
| `SkuOrderE2eTest` | 11 | e2e | API-029~036、核心输入字段口径、size JSON 往返、排序 |
| `ImportExportE2eTest` | 10 | e2e | API-037~040、CSV/Excel/BOM/策略/往返/权限 |

合计 **124 项**，全部通过。
