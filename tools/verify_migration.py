"""验证 Room 迁移 SQL 与导出的 schema 是否一致（不需要设备）。

MigrationTestHelper.runMigrationsAndValidate 做的事情本质上是：
  1. 按 vN 的 schema 建表
  2. 跑迁移 SQL
  3. 把结果与 vN+1 的 schema 对比（表结构、索引）

这里用 sqlite3 在内存里做同样的事：用 v1 的 createSql 建表、灌一条数据、
执行 CountdownDatabase 里的 MIGRATION_1_2 语句，然后与 2.json 里 Room 期望的
表结构/索引逐项比对。这样即使没有模拟器，也能提前发现"迁移写错列名/类型/默认值"。

用法: verify_migration.py <schemas_dir>
"""
import json
import os
import re
import sqlite3
import sys

# 与 CountdownDatabase.MIGRATION_1_2 保持一致（改动迁移时必须同步改这里）
MIGRATION_1_2_SQL = [
    "ALTER TABLE events ADD COLUMN mode TEXT NOT NULL DEFAULT 'COUNTDOWN'",
    # 不能写 DEFAULT NULL：sqlite 会把它记成字符串 'NULL'，与 Room 期望的无默认值不一致
    "ALTER TABLE events ADD COLUMN background_uri TEXT",
    "ALTER TABLE events ADD COLUMN background_dim REAL NOT NULL DEFAULT 0.35",
    "CREATE INDEX IF NOT EXISTS index_events_target_date ON events(target_date)",
]

SEED_SQL = """
INSERT INTO events (id, title, target_date, note, pinned, notify_enabled, created_at, updated_at)
VALUES (1, '发版纪念日', 20000, '升级前就存在的数据', 1, 1, 1700000000000, 1700000000000)
"""


def load_schema(schemas_dir: str, version: int) -> dict:
    for root, _dirs, files in os.walk(schemas_dir):
        for name in files:
            if name != f"{version}.json":
                continue
            with open(os.path.join(root, name), encoding="utf-8") as fh:
                return json.load(fh)
    raise SystemExit(f"找不到 {version}.json")


def normalize(sql: str) -> str:
    """归一化 SQL 便于比较：去多余空白、统一表名引号形式。"""
    sql = sql.strip().rstrip(";")
    sql = re.sub(r"\s+", " ", sql)
    sql = sql.replace("`", "").replace('"', "")
    return sql.lower()


def create_statements(schema: dict) -> list:
    """从 schema JSON 取出建表/建索引语句。

    Room 导出的 SQL 里用 `${TABLE_NAME}` 占位符，运行时会替换成真实表名，
    这里必须做同样的替换，否则 sqlite 建不出表。
    """
    entity = schema["database"]["entities"][0]
    table = entity["tableName"]
    stmts = [entity["createSql"].replace("${TABLE_NAME}", table)]
    for idx in entity.get("indices", []):
        stmts.append(idx["createSql"].replace("${TABLE_NAME}", table))
    return stmts


def table_info(conn: sqlite3.Connection, table: str):
    cur = conn.execute(f"PRAGMA table_info('{table}')")
    return {row[1]: (row[2].upper(), bool(row[3]), row[4]) for row in cur.fetchall()}


def index_names(conn: sqlite3.Connection, table: str):
    cur = conn.execute(f"PRAGMA index_list('{table}')")
    return sorted(row[1] for row in cur.fetchall() if not row[1].startswith("sqlite_"))


def main() -> int:
    schemas_dir = sys.argv[1] if len(sys.argv) > 1 else "app/schemas"
    v1 = load_schema(schemas_dir, 1)
    v2 = load_schema(schemas_dir, 2)
    print("schema v1 表:", v1["database"]["entities"][0]["tableName"])
    print("schema v2 表:", v2["database"]["entities"][0]["tableName"])
    print("v1 hash:", v1["database"]["identityHash"])
    print("v2 hash:", v2["database"]["identityHash"])

    failures = []

    # ---------- 1) 用 v1 schema 建库并灌数据 ----------
    conn = sqlite3.connect(":memory:")
    for stmt in create_statements(v1):
        conn.execute(stmt)
    conn.execute(SEED_SQL)
    conn.commit()
    before = conn.execute("SELECT COUNT(*) FROM events").fetchone()[0]
    print(f"\n[1] 按 v1 建表并插入 1 条数据 -> 现有 {before} 条")

    # ---------- 2) 执行迁移 ----------
    for stmt in MIGRATION_1_2_SQL:
        conn.execute(stmt)
    conn.commit()
    print(f"[2] 执行 {len(MIGRATION_1_2_SQL)} 条迁移语句完成")

    # ---------- 3) 数据是否还在 ----------
    after = conn.execute("SELECT COUNT(*) FROM events").fetchone()[0]
    print(f"[3] 迁移后记录数 = {after}（应为 {before}）")
    if after != before:
        failures.append(f"数据丢失：迁移前 {before} 条，迁移后 {after} 条")

    row = conn.execute(
        "SELECT id, title, target_date, mode, background_uri, background_dim FROM events"
    ).fetchone()
    print(f"    旧记录内容: id={row[0]} title={row[1]} target_date={row[2]} "
          f"mode={row[3]!r} background_uri={row[4]!r} background_dim={row[5]}")
    if row[1] != "发版纪念日" or row[2] != 20000:
        failures.append("旧字段内容被改动")
    if row[3] != "COUNTDOWN":
        failures.append(f"mode 默认值错误：{row[3]!r}，应为 'COUNTDOWN'")
    if row[4] is not None:
        failures.append(f"background_uri 默认值错误：{row[4]!r}，应为 NULL")
    if abs(row[5] - 0.35) > 1e-6:
        failures.append(f"background_dim 默认值错误：{row[5]!r}，应为 0.35")

    # ---------- 4) 表结构与 Room 期望的 v2 对比 ----------
    actual_cols = table_info(conn, "events")
    expected_entity = v2["database"]["entities"][0]
    # Room schema 里 affinity 已经是字符串（'INTEGER' / 'TEXT' / 'REAL' / 'BLOB'）
    affinity_map = {1: "INTEGER", 2: "REAL", 3: "TEXT", 4: "BLOB"}
    expected_cols = {}
    for f in expected_entity["fields"]:
        aff = f["affinity"]
        if isinstance(aff, int):
            aff = affinity_map.get(aff, str(aff))
        expected_cols[f["columnName"]] = (
            str(aff).upper(),
            bool(f["notNull"]),
            f.get("defaultValue"),
        )

    print("\n[4] 列对比（列名 | 期望类型/非空/默认 | 实际类型/非空/默认）")
    for name, exp in expected_cols.items():
        act = actual_cols.get(name)
        if act is None:
            failures.append(f"缺少列 {name}")
            print(f"    {name:<16} 期望 {exp} | ❌ 不存在")
            continue
        ok = act[0] == exp[0] and act[1] == exp[1]
        # 默认值比较：sqlite 会把 DEFAULT 'COUNTDOWN' 记成 'COUNTDOWN'，去引号后比较
        exp_def = (exp[2] or "").strip().strip("'")
        act_def = (str(act[2]) if act[2] is not None else "").strip().strip("'")
        ok = ok and (exp_def == act_def)
        print(f"    {name:<16} 期望 {exp} | 实际 {act} | {'OK' if ok else '❌'}")
        if not ok:
            failures.append(f"列 {name} 不匹配：期望 {exp}，实际 {act}")

    extra = set(actual_cols) - set(expected_cols)
    if extra:
        failures.append(f"多出未预期的列：{sorted(extra)}")

    # ---------- 5) 索引对比 ----------
    actual_idx = index_names(conn, "events")
    expected_idx = sorted(i["name"] for i in expected_entity.get("indices", []))
    print(f"\n[5] 索引：期望 {expected_idx} | 实际 {actual_idx}")
    if sorted(actual_idx) != expected_idx:
        failures.append(f"索引不匹配：期望 {expected_idx}，实际 {actual_idx}")

    # ---------- 6) 索引对应的建表语句也要能对上 ----------
    for idx in expected_entity.get("indices", []):
        want = normalize(idx["createSql"])
        got_rows = conn.execute(
            "SELECT sql FROM sqlite_master WHERE type='index' AND name=?", (idx["name"],)
        ).fetchone()
        got = normalize(got_rows[0]) if got_rows and got_rows[0] else ""
        # sqlite 会自动补 IF NOT EXISTS 之类的差异，这里只比对列部分
        want_cols = re.search(r"\((.*)\)", want)
        got_cols = re.search(r"\((.*)\)", got)
        same = bool(want_cols and got_cols and want_cols.group(1) == got_cols.group(1))
        print(f"    索引 {idx['name']} 列定义: 期望 {want_cols.group(1) if want_cols else '?'} | "
              f"实际 {got_cols.group(1) if got_cols else '?'} | {'OK' if same else '❌'}")
        if not same:
            failures.append(f"索引 {idx['name']} 列定义不一致")

    conn.close()

    print()
    if failures:
        print("=" * 64)
        print("迁移校验失败，发现以下问题：")
        for f in failures:
            print("  ❌", f)
        return 1
    print("=" * 64)
    print("迁移校验通过 ✓ 迁移后的表结构与 schema v2 完全一致，旧数据完整保留")
    return 0


if __name__ == "__main__":
    sys.exit(main())
