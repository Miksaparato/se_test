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
