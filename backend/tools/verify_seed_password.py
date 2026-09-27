"""独立校验 V3 种子账号的 BCrypt 口令。

从 stdin 读取 `account<分隔><hash>`，分隔符同时接受真实制表符与字面量 `\\t`
（Windows PowerShell 管道会把制表符转写为字面量 `\\t`）。
"""
import re
import sys

import bcrypt

PLAINTEXT = b"admin123"
expected = {"admin", "operator", "analyst", "viewer"}
seen = set()
failed = 0

for line in sys.stdin.read().splitlines():
    line = line.strip()
    if not line:
        continue
    parts = re.split(r"\t|\\t", line, maxsplit=1)
    if len(parts) != 2:
        continue
    account, hashed = parts[0].strip(), parts[1].strip()
    seen.add(account)
    ok = False
    try:
        ok = bcrypt.checkpw(PLAINTEXT, hashed.encode())
    except ValueError as exc:  # 非法哈希格式
        print(f"  [FAIL] {account}: 哈希格式非法 ({exc})")
        failed += 1
        continue
    print(f"  {account:<9} admin123 -> {ok}")
    if not ok:
        failed += 1

missing = expected - seen
for account in sorted(missing):
    print(f"  [FAIL] 缺少账号 {account}")
    failed += 1

print("[OK] 全部 4 个种子账号口令校验通过" if failed == 0 else f"[FAIL] {failed} 项未通过")
sys.exit(1 if failed else 0)
