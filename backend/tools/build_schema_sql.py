"""把 Flyway 版本化脚本合并为一份可直接执行的建表脚本（供手工建库/交付评审使用）。

生成的 `backend/sql/schema.sql` 内容 = V1 + V2 + V3 按顺序拼接，
并在开头加上建库语句与执行说明。**唯一事实来源仍是 db/migration 下的 Flyway 脚本**，
本文件是它们的派生副本，请勿手工修改——改了 Flyway 脚本后重新运行本脚本即可。

用法：python tools/build_schema_sql.py
"""

from __future__ import annotations

from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
MIGRATION_DIR = BASE / "src" / "main" / "resources" / "db" / "migration"
OUTPUT = BASE / "sql" / "schema.sql"

MIGRATIONS = [
    "V1__init_base_data.sql",
    "V2__init_rbac.sql",
    "V3__seed_rbac.sql",
]

HEADER = """-- =============================================================================
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
--     本机示例：& "C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe" `
--                    -u root -p --default-character-set=utf8mb4 < backend\\sql\\schema.sql
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

"""


def main() -> None:
    """生成合并后的建表脚本。"""
    parts = [HEADER]
    for name in MIGRATIONS:
        path = MIGRATION_DIR / name
        if not path.exists():
            raise SystemExit(f"[FAIL] 缺少迁移脚本：{path}")
        content = path.read_text(encoding="utf-8").strip("\n")
        parts.append(
            "-- =============================================================================\n"
            f"-- 来源：db/migration/{name}\n"
            "-- =============================================================================\n\n"
            f"{content}\n\n"
        )

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text("".join(parts), encoding="utf-8", newline="\n")

    size_kb = OUTPUT.stat().st_size / 1024
    create_count = sum(
        (MIGRATION_DIR / name).read_text(encoding="utf-8").count("CREATE TABLE")
        for name in MIGRATIONS
    )
    print(f"已生成 {OUTPUT.relative_to(BASE)}（{size_kb:.1f} KB，{create_count} 张表）")


if __name__ == "__main__":
    main()
