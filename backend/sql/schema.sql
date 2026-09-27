-- =============================================================================
-- 小型智能仓储库位分配仿真系统 —— 完整建表脚本（含种子数据）
--
-- 本文件由 backend/tools/build_schema_sql.py 自动生成，内容为 Flyway 脚本
-- V1 + V2 + V3 的顺序拼接，**请勿手工修改**。
-- 唯一事实来源：backend/src/main/resources/db/migration/V*.sql
--
-- 方式一（推荐）：交给 Flyway 自动执行——应用启动时按版本号顺序执行，无需手工干预
--     CREATE DATABASE wms_sim DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
--     cd backend && mvn -s maven-settings.xml spring-boot:run
--
-- 方式二：手工执行本脚本（适合数据库评审、无 Maven 环境或需预建库）
--     mysql -u root -p --default-character-set=utf8mb4 < schema.sql
--     mysql -u root -p --default-character-set=utf8mb4 wms_sim < schema.sql   # 库已建好时
--     本机示例：& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" `
--                    -u root -p --default-character-set=utf8mb4 < backend\sql\schema.sql
--
-- 注意：
--   1. 必须带 --default-character-set=utf8mb4，否则中文表注释与种子数据会乱码；
--   2. 本脚本是「首次建库」脚本：表已存在时会报 ERROR 1050（Table already exists），
--      这是有意为之——重复执行不会静默跳过，避免掩盖版本不一致。
--      需要重新初始化时先执行：
--          DROP DATABASE IF EXISTS `wms_sim`;
--   3. 脚本中的 INSERT 使用 INSERT IGNORE：重复写入不会覆盖已被管理员修改的密码与权限；
--   4. 使用 Flyway（方式一）时无需关心以上问题：Flyway 按版本号只执行一次，并记录在
--      flyway_schema_history 表中。
--
-- 执行完成后应得到 13 张表（12 张业务表 + flyway_schema_history 由 Flyway 建）
-- 与种子数据：9 权限 / 4 内置角色 / 19 条角色权限 / 4 个默认账号（初始密码 admin123）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 建库（已建库时该语句可跳过）
-- -----------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `wms_sim`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE `wms_sim`;

-- =============================================================================
-- 来源：db/migration/V1__init_base_data.sql
-- =============================================================================

-- =============================================================================
-- 小型智能仓储库位分配仿真系统 —— V1 基础数据表
-- 负责人：a（谢卓成）  COM-2 数据库表结构设计
-- 依据：《数据库设计说明书》V1.0 第 2 节、《需求文档》6.1、《接口文档》API-016~040
-- 数据库：MySQL 8.0（InnoDB / utf8mb4）
-- 说明：本脚本仅建表，不写入任何业务数据；RBAC 表见 V2，种子数据见 V3。
-- 建表顺序：被引用表必须先行创建（warehouses → skus → racks → locations → orders → plans）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. warehouses 仓库
--    FR-1.1 仓库建模；出库口坐标供曼哈顿距离计算使用（COM-6，c 牵头）
-- -----------------------------------------------------------------------------
CREATE TABLE `warehouses`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`       VARCHAR(32)     NOT NULL COMMENT '仓库编码，全局唯一，如 WH-01',
    `name`       VARCHAR(64)     NOT NULL COMMENT '仓库名称，如 一号仓',
    `length`     INT UNSIGNED    NULL     DEFAULT NULL COMMENT '仓库长（米/格），FR-1.1',
    `width`      INT UNSIGNED    NULL     DEFAULT NULL COMMENT '仓库宽（米/格），FR-1.1',
    `height`     INT UNSIGNED    NULL     DEFAULT NULL COMMENT '仓库高 / 最大层数，FR-1.1',
    `exit_x`     INT             NOT NULL DEFAULT 0 COMMENT '出库口平面坐标 x，距离计算终点',
    `exit_y`     INT             NOT NULL DEFAULT 0 COMMENT '出库口平面坐标 y，距离计算终点',
    `exit_layer` INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '出库口层号，默认 1（预留给三维扩展）',
    `remark`     VARCHAR(255)    NULL     DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_warehouses_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='仓库';

-- -----------------------------------------------------------------------------
-- 2. skus 货物
--    weight / turnover_rate / priority 是推荐引擎（b）与仿真引擎（c）的核心输入
-- -----------------------------------------------------------------------------
CREATE TABLE `skus`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`          VARCHAR(32)     NOT NULL COMMENT 'SKU 编码，全局唯一，如 SKU-001',
    `name`          VARCHAR(64)     NOT NULL COMMENT '货物名称',
    `weight`        DECIMAL(12, 3)  NOT NULL DEFAULT 0.000 COMMENT '重量(kg)，评分 S_weight 输入',
    `turnover_rate` DECIMAL(12, 4)  NOT NULL DEFAULT 0.0000 COMMENT '周转频次(次/单位时间)，评分 S_freq 输入',
    `priority`      INT             NOT NULL DEFAULT 1 COMMENT '出库优先级 1~5，越大越紧急，评分 S_priority 输入',
    `category`      VARCHAR(32)     NULL     DEFAULT NULL COMMENT '品类，评分 S_other 分区匹配项',
    `size`          JSON            NULL     DEFAULT NULL COMMENT '尺寸 {"length":30,"width":20,"height":10}',
    `remark`        VARCHAR(255)    NULL     DEFAULT NULL COMMENT '备注',
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skus_code` (`code`),
    KEY `idx_skus_category` (`category`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='货物(SKU)';

-- -----------------------------------------------------------------------------
-- 3. racks 货架
--    库位编码规则：巷道-列-层，如 A-01-03-02；本表声明货架规模，locations 存库位实例
-- -----------------------------------------------------------------------------
CREATE TABLE `racks`
(
    `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `warehouse_id` BIGINT UNSIGNED NOT NULL COMMENT '所属仓库 id',
    `code`         VARCHAR(32)     NOT NULL COMMENT '货架编码，仓库内唯一，如 A-01',
    `aisle`        VARCHAR(16)     NOT NULL COMMENT '巷道（A/B/C...），库位编码第 1 段',
    `column_count` INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '列数，库位编码第 2 段',
    `layer_count`  INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '层数，库位编码第 3 段',
    `x`            INT             NULL     DEFAULT NULL COMMENT '货架基准平面坐标 x（平面图渲染）',
    `y`            INT             NULL     DEFAULT NULL COMMENT '货架基准平面坐标 y（平面图渲染）',
    `orientation`  VARCHAR(16)     NOT NULL DEFAULT 'row' COMMENT '库位排布方向：row 沿 x 递增 / column 沿 y 递增',
    `created_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_racks_warehouse_code` (`warehouse_id`, `code`),
    KEY `idx_racks_warehouse` (`warehouse_id`),
    CONSTRAINT `fk_racks_warehouse_id` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='货架';

-- -----------------------------------------------------------------------------
-- 4. locations 库位
--    推荐引擎（b）与仿真引擎（c）的核心数据来源：status / x / y / layer / capacity
--    warehouse_id 为冗余列，用于避免万库位查询回表 JOIN racks（NFR-1）
-- -----------------------------------------------------------------------------
CREATE TABLE `locations`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `rack_id`         BIGINT UNSIGNED NOT NULL COMMENT '所属货架 id',
    `warehouse_id`    BIGINT UNSIGNED NOT NULL COMMENT '所属仓库 id（冗余，由业务保证与货架一致）',
    `code`            VARCHAR(32)     NOT NULL COMMENT '库位唯一编码 巷道-列-层，如 A-01-03-02，全局唯一',
    `x`               INT             NOT NULL COMMENT '平面坐标 x（曼哈顿距离起点）',
    `y`               INT             NOT NULL COMMENT '平面坐标 y（曼哈顿距离起点）',
    `layer`           INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '层号，从 1 开始，层号越小越靠地面（S_weight 依据）',
    `status`          VARCHAR(16)     NOT NULL DEFAULT 'free' COMMENT '状态：free 空闲 / occupied 占用 / disabled 停用',
    `capacity`        DECIMAL(12, 3)  NOT NULL DEFAULT 100.000 COMMENT '库位容量（体积口径）',
    `occupied_sku_id` BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '当前占用货物 id，status=occupied 时必须非空',
    `created_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_locations_code` (`code`),
    KEY `idx_locations_warehouse_status` (`warehouse_id`, `status`),
    KEY `idx_locations_rack` (`rack_id`),
    KEY `idx_locations_occupied_sku` (`occupied_sku_id`),
    CONSTRAINT `fk_locations_rack_id` FOREIGN KEY (`rack_id`) REFERENCES `racks` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_locations_warehouse_id` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT `fk_locations_occupied_sku_id` FOREIGN KEY (`occupied_sku_id`) REFERENCES `skus` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='库位';

-- -----------------------------------------------------------------------------
-- 5. orders 出库订单
--    本期一张订单一条 SKU 明细（与《需求文档》6.1 一致）
--    出库仿真排序：priority DESC, placed_at ASC（FR-4.1，索引 idx_orders_status_priority_placed）
-- -----------------------------------------------------------------------------
CREATE TABLE `orders`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `order_no`   VARCHAR(40)     NOT NULL COMMENT '订单号，全局唯一，如 SO-20260910-001',
    `sku_id`     BIGINT UNSIGNED NOT NULL COMMENT '货物 id',
    `quantity`   INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '出库数量',
    `priority`   INT             NOT NULL DEFAULT 1 COMMENT '订单优先级 1~5，越大越紧急',
    `placed_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下达时间(UTC)',
    `status`     VARCHAR(16)     NOT NULL DEFAULT 'pending' COMMENT '状态：pending 待出库 / picking 拣选中 / completed 已完成 / cancelled 已取消',
    `remark`     VARCHAR(255)    NULL     DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_orders_order_no` (`order_no`),
    KEY `idx_orders_sku` (`sku_id`),
    KEY `idx_orders_status_priority_placed` (`status`, `priority` DESC, `placed_at` ASC),
    CONSTRAINT `fk_orders_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `skus` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='出库订单';

-- -----------------------------------------------------------------------------
-- 6. plans 分配方案（c 写、b 读）
--    location_map / result 的 JSON 结构见《数据库设计说明书》2.6，字段名与 API-051 对齐
--    created_by 引用 users（V2 建表），故本表在 V2 之后再补该外键：见 V2 末尾
-- -----------------------------------------------------------------------------
CREATE TABLE `plans`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键（对外 planId）',
    `plan_no`       VARCHAR(40)     NOT NULL COMMENT '方案编号，全局唯一，如 PLAN-20260910-001',
    `name`          VARCHAR(64)     NULL     DEFAULT NULL COMMENT '方案名称',
    `warehouse_id`  BIGINT UNSIGNED NOT NULL COMMENT '所属仓库 id',
    `simulation_id` VARCHAR(40)     NULL     DEFAULT NULL COMMENT '产出该方案的仿真单号，如 INB-001',
    `strategy`      VARCHAR(32)     NOT NULL COMMENT '策略标识：random/nearest/zoning/grading/smart/fifo',
    `strategy_name` VARCHAR(32)     NULL     DEFAULT NULL COMMENT '策略中文展示名，如 智能推荐',
    `params`        JSON            NULL     DEFAULT NULL COMMENT '策略参数快照',
    `location_map`  JSON            NULL     DEFAULT NULL COMMENT '库位占用映射：{"库位id":{"skuId":3,"quantity":20}}',
    `result`        JSON            NULL     DEFAULT NULL COMMENT '仿真统计结果：totalDistance/avgDistance/hotAvgDistance/violations 等',
    `created_by`    BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '创建人 id（外键在 V2 中补充）',
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plans_plan_no` (`plan_no`),
    KEY `idx_plans_warehouse` (`warehouse_id`),
    KEY `idx_plans_strategy` (`strategy`),
    CONSTRAINT `fk_plans_warehouse_id` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='库位分配方案';

-- =============================================================================
-- 来源：db/migration/V2__init_rbac.sql
-- =============================================================================

-- =============================================================================
-- 小型智能仓储库位分配仿真系统 —— V2 RBAC 表
-- 负责人：a（谢卓成）  COM-2 / FR-RBAC-1~5
-- 依据：《数据库设计说明书》V1.0 第 3 节、《接口文档》1.4、《代码规范》4.3
-- 数据库：MySQL 8.0（InnoDB / utf8mb4）
-- 说明：与《需求文档》6.1 的差异 —— role_ids / permissions 两个 JSON 列规范化为
--       user_roles、role_permissions 关联表（对外接口 JSON 契约不变，见设计说明书 3.5/3.6）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. users 用户
--    password 存 BCrypt 哈希（$2a$10$...，60 字符），永不返回前端（NFR-6）
-- -----------------------------------------------------------------------------
CREATE TABLE `users`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `account`       VARCHAR(32)     NOT NULL COMMENT '登录账号，全局唯一',
    `password`      VARCHAR(100)    NOT NULL COMMENT '密码 BCrypt 哈希，禁止明文/可逆加密',
    `name`          VARCHAR(64)     NULL     DEFAULT NULL COMMENT '姓名/昵称',
    `email`         VARCHAR(128)    NULL     DEFAULT NULL COMMENT '邮箱',
    `phone`         VARCHAR(32)     NULL     DEFAULT NULL COMMENT '手机号',
    `status`        VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT '账号状态：active 启用 / disabled 禁用',
    `last_login_at` DATETIME(3)     NULL     DEFAULT NULL COMMENT '最近登录时间(UTC)',
    `remark`        VARCHAR(255)    NULL     DEFAULT NULL COMMENT '备注',
    `created_by`    BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '创建人用户 id（不建外键，避免自引用）',
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_account` (`account`),
    KEY `idx_users_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户';

-- -----------------------------------------------------------------------------
-- 2. roles 角色
--    is_builtin = 1 的内置角色禁止删除（FR-RBAC-4），但其权限可被管理员调整
-- -----------------------------------------------------------------------------
CREATE TABLE `roles`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`        VARCHAR(32)     NOT NULL COMMENT '角色标识，全局唯一：admin/operator/analyst/viewer',
    `name`        VARCHAR(64)     NOT NULL COMMENT '角色名称（中文），如 系统管理员',
    `description` VARCHAR(255)    NULL     DEFAULT NULL COMMENT '角色描述',
    `is_builtin`  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否内置角色：1 内置(禁删) / 0 自定义',
    `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_roles_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色';

-- -----------------------------------------------------------------------------
-- 3. permissions 权限
--    权限码必须来自《接口文档》1.4 清单，禁止自行发明（《代码规范》4.3）
--    type：menu 菜单可见性 / action 操作（含后端接口鉴权）
-- -----------------------------------------------------------------------------
CREATE TABLE `permissions`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `code`       VARCHAR(64)     NOT NULL COMMENT '权限码，全局唯一，如 user:manage',
    `name`       VARCHAR(64)     NOT NULL COMMENT '权限名称（中文）',
    `type`       VARCHAR(16)     NOT NULL DEFAULT 'action' COMMENT '权限类型：menu 菜单 / action 操作',
    `sort_no`    INT             NOT NULL DEFAULT 0 COMMENT '展示排序',
    `remark`     VARCHAR(255)    NULL     DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    `updated_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permissions_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='权限';

-- -----------------------------------------------------------------------------
-- 4. user_roles 用户-角色关联（多对多）
-- -----------------------------------------------------------------------------
CREATE TABLE `user_roles`
(
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户 id',
    `role_id`    BIGINT UNSIGNED NOT NULL COMMENT '角色 id',
    `created_at` DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_roles` (`user_id`, `role_id`),
    KEY `idx_user_roles_role` (`role_id`),
    CONSTRAINT `fk_user_roles_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `fk_user_roles_role_id` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户-角色关联';

-- -----------------------------------------------------------------------------
-- 5. role_permissions 角色-权限关联（多对多）
--    API-014 为「全量覆盖」语义：先 DELETE 该角色关联，再批量 INSERT
-- -----------------------------------------------------------------------------
CREATE TABLE `role_permissions`
(
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`       BIGINT UNSIGNED NOT NULL COMMENT '角色 id',
    `permission_id` BIGINT UNSIGNED NOT NULL COMMENT '权限 id',
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permissions` (`role_id`, `permission_id`),
    KEY `idx_role_permissions_permission` (`permission_id`),
    CONSTRAINT `fk_role_permissions_role_id` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT `fk_role_permissions_permission_id` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色-权限关联';

-- -----------------------------------------------------------------------------
-- 6. audit_logs 操作审计日志（可选）
--    《代码规范》4.6：关键路径（登录/入库/出库/推荐/越权）记录操作人、资源、结果
--    禁止写入密码、Token 等敏感信息
-- -----------------------------------------------------------------------------
CREATE TABLE `audit_logs`
(
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '操作人 id',
    `account`     VARCHAR(32)     NULL     DEFAULT NULL COMMENT '操作人账号快照（用户删除后仍可追溯）',
    `action`      VARCHAR(32)     NOT NULL COMMENT '行为：login/logout/create/update/delete/adopt/denied 等',
    `resource`    VARCHAR(64)     NULL     DEFAULT NULL COMMENT '资源，如 skus/roles/locations',
    `resource_id` VARCHAR(64)     NULL     DEFAULT NULL COMMENT '资源 id',
    `detail`      JSON            NULL     DEFAULT NULL COMMENT '详情（禁止写入密码、Token）',
    `ip`          VARCHAR(45)     NULL     DEFAULT NULL COMMENT '客户端 IP（兼容 IPv6）',
    `created_at`  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间(UTC)',
    PRIMARY KEY (`id`),
    KEY `idx_audit_logs_user` (`user_id`),
    KEY `idx_audit_logs_created` (`created_at`),
    CONSTRAINT `fk_audit_logs_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='操作审计日志';

-- -----------------------------------------------------------------------------
-- 7. 补充 plans.created_by 外键
--    V1 创建 plans 时 users 尚不存在，故在此补建（保证「先建被引用表」原则）
-- -----------------------------------------------------------------------------
ALTER TABLE `plans`
    ADD CONSTRAINT `fk_plans_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT;

-- =============================================================================
-- 来源：db/migration/V3__seed_rbac.sql
-- =============================================================================

-- =============================================================================
-- 小型智能仓储库位分配仿真系统 —— V3 RBAC 种子数据
-- 负责人：a（谢卓成）  FR-RBAC-1 / FR-RBAC-4
-- 依据：《数据库设计说明书》V1.0 第 4 节、《接口文档》1.4 权限清单与角色映射
-- 数据库：MySQL 8.0（InnoDB / utf8mb4）
--
-- 内容：
--   1) 9 条权限（取自《接口文档》1.4，id 固定 1~9）
--   2) 4 个内置角色：admin / operator / analyst / viewer
--   3) 角色-权限映射：admin 9 项、operator 4 项、analyst 4 项、viewer 2 项（共 19 条）
--   4) 4 个默认账号，初始密码统一为 admin123（BCrypt 哈希，strength=10，$2a$ 前缀）
--
-- 安全声明（《代码规范》4.6 / NFR-6）：
--   * 明文密码仅用于本地验收与《使用说明文档》，生产/演示部署前必须改密或删除测试账号。
--   * 密码哈希算法：Spring Security BCryptPasswordEncoder（strength = 10），前缀 $2a$。
--   * 本脚本使用 INSERT IGNORE：在开发环境重复执行不会报错，也不会覆盖已被管理员修改的
--     密码与权限（Flyway 正常只会执行一次）。
--
-- 文档冲突说明：viewer 的权限以《接口文档》1.4 为准（仅 sim:view、compare:view），
--   《需求分析文档》2.2 权限矩阵中 viewer「系统配置」标记为 ✔ 判定为笔误，
--   详见《数据库设计说明书》3.4 待澄清项。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 权限（9 条，码值禁止改动；新增权限须由 a 更新本表与《接口文档》1.4）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `permissions` (`id`, `code`, `name`, `type`, `sort_no`, `remark`)
VALUES (1, 'user:manage', '用户/角色/权限管理', 'menu', 10, '用户、角色、权限管理页面与接口'),
       (2, 'warehouse:manage', '仓库/货架/库位管理', 'action', 20, '仓库/货架/库位增删改'),
       (3, 'sku:manage', '货物/订单管理', 'action', 30, 'SKU 与订单增删改、批量导入'),
       (4, 'recommend:view', '库位智能推荐', 'menu', 40, '推荐页面与「采用推荐」'),
       (5, 'sim:run', '入库策略仿真', 'action', 50, '仿真执行、随机订单集生成'),
       (6, 'sim:view', '仿真结果查看', 'menu', 60, '仓库布局、库位/货物/订单查询、仿真结果'),
       (7, 'compare:view', '方案对比与优化建议', 'menu', 70, '方案列表/详情/对比/建议'),
       (8, 'report:export', '报告导出', 'action', 80, '报告与数据导出'),
       (9, 'config:manage', '系统配置（权重/参数）', 'action', 90, '评分权重与分层规则配置');

-- -----------------------------------------------------------------------------
-- 2. 内置角色（is_builtin = 1：禁止删除，权限可被管理员调整 —— FR-RBAC-4）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `roles` (`id`, `code`, `name`, `description`, `is_builtin`)
VALUES (1, 'admin', '系统管理员', '最高权限，负责系统配置、基础数据与用户权限管理', 1),
       (2, 'operator', '仓库操作员', '货物/订单录入、库位推荐、运行入库仿真', 1),
       (3, 'analyst', '分析人员', '运行仿真、查看结果、方案对比与报告导出', 1),
       (4, 'viewer', '只读访客', '仅查看仓库布局与仿真结果，无修改权限', 1);

-- -----------------------------------------------------------------------------
-- 3. 角色-权限映射（共 19 条，《接口文档》1.4「允许角色」列）
-- -----------------------------------------------------------------------------
-- admin：全部 9 项
INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
VALUES (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8), (1, 9);

-- operator：sku:manage(3)、recommend:view(4)、sim:run(5)、sim:view(6)
INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
VALUES (2, 3), (2, 4), (2, 5), (2, 6);

-- analyst：sim:run(5)、sim:view(6)、compare:view(7)、report:export(8)
INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
VALUES (3, 5), (3, 6), (3, 7), (3, 8);

-- viewer：sim:view(6)、compare:view(7)
INSERT IGNORE INTO `role_permissions` (`role_id`, `permission_id`)
VALUES (4, 6), (4, 7);

-- -----------------------------------------------------------------------------
-- 4. 默认账号（初始密码统一 admin123，BCrypt 哈希）
--    首次登录后请通过 API-004 修改密码
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `users` (`id`, `account`, `password`, `name`, `email`, `status`, `remark`)
VALUES (1, 'admin', '$2a$10$hm2S8wL2GKbgWQbQBhBkf.ZPXY34Oj6ImINkoWmuGSNvw1H7DxfAq', '系统管理员',
        'admin@wms.local', 'active', '内置管理员，初始密码 admin123，首次登录后请修改'),
       (2, 'operator', '$2a$10$/kxN0KEhbX4ZdSGBlL22KehJ6FUuS3QeVd8w.lSXB8/dse9QpatXm', '仓库操作员',
        'operator@wms.local', 'active', '验收测试账号，初始密码 admin123'),
       (3, 'analyst', '$2a$10$nX1LxxcZ4XgxyI8vsr2JneMRq9GSJ8ljtSKlJK95qx47Cq8EwnDme', '分析人员',
        'analyst@wms.local', 'active', '验收测试账号，初始密码 admin123'),
       (4, 'viewer', '$2a$10$cFWxpvD.PU9BIPgHg.kbFexrbYWriFENQnc5kolDblU/pvJkO6Zt.', '只读访客',
        'viewer@wms.local', 'active', '验收测试账号，初始密码 admin123');

-- -----------------------------------------------------------------------------
-- 5. 用户-角色绑定（一人一角色，满足 2.1 角色定义；多角色能力由表结构支持）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO `user_roles` (`user_id`, `role_id`)
VALUES (1, 1), (2, 2), (3, 3), (4, 4);

