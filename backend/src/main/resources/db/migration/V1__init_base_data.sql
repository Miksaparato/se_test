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
