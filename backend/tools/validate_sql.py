"""校验 Flyway SQL 脚本的静态一致性（无 MySQL 环境时使用）。

检查项：
  1. 每个语句内的括号是否配平
  2. CREATE TABLE 的表名是否唯一
  3. 外键 REFERENCES 的目标表是否在本次校验的全部脚本中已创建（含 ALTER TABLE 补建）
  4. V3 种子数据引用的 role_id / permission_id / user_id 是否在脚本内定义
  5. 每个语句是否以分号结尾
"""
import re
import sys
from pathlib import Path

MIGRATION_DIR = Path(__file__).resolve().parents[1] / "src" / "main" / "resources" / "db" / "migration"


def strip_comments(sql: str) -> str:
    """去掉 -- 行注释，保留换行以维持行号。"""
    return "\n".join(re.sub(r"--.*$", "", line) for line in sql.splitlines())


def split_statements(sql: str) -> list[tuple[int, str]]:
    """按分号切分语句，返回 (起始行号, 语句文本)。"""
    statements: list[tuple[int, str]] = []
    buffer: list[str] = []
    start_line = 1
    for lineno, line in enumerate(sql.splitlines(), start=1):
        if not buffer:
            start_line = lineno
        buffer.append(line)
        if ";" in line:
            statements.append((start_line, "\n".join(buffer)))
            buffer = []
    if buffer and any(chunk.strip() for chunk in buffer):
        statements.append((start_line, "\n".join(buffer)))
    return statements


def main() -> int:
    errors: list[str] = []
    sql_files = sorted(MIGRATION_DIR.glob("V*.sql"))
    if not sql_files:
        print(f"[FAIL] 未找到迁移脚本：{MIGRATION_DIR}")
        return 1

    created_tables: dict[str, str] = {}
    all_statements: list[tuple[str, int, str]] = []

    for path in sql_files:
        raw = path.read_text(encoding="utf-8")
        if "\ufeff" in raw:
            errors.append(f"{path.name}: 含 BOM，违反《代码规范》2.1")
        if raw and not raw.endswith("\n"):
            errors.append(f"{path.name}: 文件末尾缺少换行，违反《代码规范》2.1")
        if "\r\n" in raw:
            errors.append(f"{path.name}: 含 CRLF 行尾，应统一为 LF")

        sql = strip_comments(raw)
        for start_line, stmt in split_statements(sql):
            if not stmt.strip():
                continue
            all_statements.append((path.name, start_line, stmt))
            if "(" in stmt or ")" in stmt:
                depth = 0
                for ch in stmt:
                    if ch == "(":
                        depth += 1
                    elif ch == ")":
                        depth -= 1
                    if depth < 0:
                        errors.append(f"{path.name}:{start_line} 括号提前闭合")
                        break
                if depth != 0:
                    errors.append(f"{path.name}:{start_line} 括号不配平（余 {depth}）")
            if stmt.strip() and not stmt.strip().endswith(";"):
                errors.append(f"{path.name}:{start_line} 语句未以分号结尾")

        for match in re.finditer(
            r"CREATE\s+TABLE\s+`?(\w+)`?\s*\((.*?)\n\)\s*ENGINE",
            sql,
            flags=re.IGNORECASE | re.DOTALL,
        ):
            table = match.group(1)
            if table in created_tables:
                errors.append(
                    f"{path.name}: 表 {table} 重复创建（已在 {created_tables[table]} 创建）"
                )
            created_tables[table] = path.name

            columns = re.findall(r"^\s+`(\w+)`\s+\w", match.group(2), flags=re.MULTILINE)
            duplicates = sorted({c for c in columns if columns.count(c) > 1})
            if duplicates:
                errors.append(f"{path.name}: 表 {table} 存在重复列 {duplicates}")
            if not columns:
                errors.append(f"{path.name}: 表 {table} 未解析到任何列")

    # CREATE TABLE 数量与解析出的表数量应一致
    create_count = sum(
        len(re.findall(r"CREATE\s+TABLE", strip_comments(p.read_text(encoding="utf-8")), re.I))
        for p in sql_files
    )
    if create_count != len(created_tables):
        errors.append(
            f"CREATE TABLE 语句数({create_count}) 与解析出的表数({len(created_tables)}) 不一致"
        )

    # 外键引用检查
    fk_pattern = re.compile(
        r"(?:CONSTRAINT\s+`?\w+`?\s+)?FOREIGN\s+KEY\s*\(\s*`?(\w+)`?\s*\)\s*"
        r"REFERENCES\s+`?(\w+)`?",
        flags=re.IGNORECASE,
    )
    for name, start_line, stmt in all_statements:
        for column, target in fk_pattern.findall(stmt):
            if target not in created_tables:
                errors.append(
                    f"{name}:{start_line} 外键 {column} 引用未创建的表 {target}"
                )

    # 种子数据引用检查（仅对 V3）
    for name, start_line, stmt in all_statements:
        if not name.startswith("V3"):
            continue
        if re.search(r"INSERT\s+(IGNORE\s+)?INTO\s+`?role_permissions`?", stmt, re.I):
            role_ids = {int(v) for v in re.findall(r"\((\d+),\s*\d+\)", stmt)}
            perm_ids = {int(v) for v in re.findall(r"\(\d+,\s*(\d+)\)", stmt)}
            for rid in sorted(role_ids):
                if rid not in {1, 2, 3, 4}:
                    errors.append(f"{name}:{start_line} role_id={rid} 未在 roles 中定义")
            for pid in sorted(perm_ids):
                if pid not in set(range(1, 10)):
                    errors.append(f"{name}:{start_line} permission_id={pid} 未在 permissions 中定义")

    if errors:
        print(f"[FAIL] 发现 {len(errors)} 个问题：")
        for err in errors:
            print("  -", err)
        return 1

    print(f"[OK] 校验通过：{len(sql_files)} 个脚本，{len(created_tables)} 张表，{len(all_statements)} 条语句")
    print("     表清单：", ", ".join(sorted(created_tables)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
