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
