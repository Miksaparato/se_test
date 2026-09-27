"""一次性代码生成器：按数据库 schema 生成 MyBatis-Plus 实体类。

生成物遵循《代码规范》：
  * 命名：类 PascalCase、字段 camelCase、表名复数 snake_case
  * 每个类与每个 getter/setter 均有 Javadoc
  * 文件 UTF-8 无 BOM、LF 行尾、末尾一个空行

用法：python tools/generate_entities.py
生成目标：src/main/java/com/wms/domain/entity/
"""

from __future__ import annotations

from pathlib import Path

BASE = Path(__file__).resolve().parents[1]
ENTITY_DIR = BASE / "src" / "main" / "java" / "com" / "wms" / "domain" / "entity"


def camel(snake: str) -> str:
    """snake_case → camelCase。"""
    head, *rest = snake.split("_")
    return head + "".join(part.capitalize() for part in rest)


def pascal(snake: str) -> str:
    """snake_case → PascalCase。"""
    return "".join(part.capitalize() for part in snake.split("_"))


# (表名, 类名, 类注释, 字段定义)
# 字段定义: (列名, Java 类型, 字段注释, 注解或 None)
SCHEMA = [
    (
        "warehouses",
        "Warehouse",
        ["仓库实体，对应表 {@code warehouses}。",
         "",
         "<p>出库口坐标（{@code exit_x} / {@code exit_y}）是曼哈顿距离计算的终点（COM-6，由 c 牵头统一口径），",
         "在 API-021 仓库平面布局中以 {@code exit {x, y}} 返回。"],
        [
            ("id", "Long", "主键", "@TableId(type = IdType.AUTO)"),
            ("code", "String", "仓库编码，全局唯一，如 WH-01", None),
            ("name", "String", "仓库名称，如 一号仓", None),
            ("length", "Integer", "仓库长（米/格），FR-1.1", None),
            ("width", "Integer", "仓库宽（米/格），FR-1.1", None),
            ("height", "Integer", "仓库高 / 最大层数，FR-1.1", None),
            ("exit_x", "Integer", "出库口平面坐标 x，距离计算终点", None),
            ("exit_y", "Integer", "出库口平面坐标 y，距离计算终点", None),
            ("exit_layer", "Integer", "出库口层号，默认 1（预留给三维扩展）", None),
            ("remark", "String", "备注", None),
            ("created_at", "LocalDateTime", "创建时间(UTC)，插入时自动填充",
             '@TableField(value = "created_at", fill = FieldFill.INSERT)'),
            ("updated_at", "LocalDateTime", "更新时间(UTC)，插入与更新时自动填充",
             '@TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)'),
        ],
    ),
    (
        "racks",
        "Rack",
        ["货架实体，对应表 {@code racks}。",
         "",
         "<p>库位编码规则为「巷道-货架序号-列-层」，如 {@code A-01-03-02}；",
         "本表声明货架规模（{@code column_count} / {@code layer_count}），",
         "库位实例存于 {@code locations} 表。"],
        [
            ("id", "Long", "主键", "@TableId(type = IdType.AUTO)"),
            ("warehouse_id", "Long", "所属仓库 id", None),
            ("code", "String", "货架编码，仓库内唯一，如 A-01", None),
            ("aisle", "String", "巷道（A/B/C...），库位编码第 1 段", None),
            ("column_count", "Integer", "列数，库位编码第 3 段", None),
            ("layer_count", "Integer", "层数，库位编码第 4 段", None),
            ("x", "Integer", "货架基准平面坐标 x（平面图渲染）", None),
            ("y", "Integer", "货架基准平面坐标 y（平面图渲染）", None),
            ("orientation", "String", "库位排布方向：row 沿 x 递增 / column 沿 y 递增", None),
            ("created_at", "LocalDateTime", "创建时间(UTC)，插入时自动填充",
             '@TableField(value = "created_at", fill = FieldFill.INSERT)'),
            ("updated_at", "LocalDateTime", "更新时间(UTC)，插入与更新时自动填充",
             '@TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)'),
        ],
    ),
    (
        "locations",
        "Location",
        ["库位实体，对应表 {@code locations}。",
         "",
         "<p><b>这是推荐引擎（b）与仿真引擎（c）的核心数据来源</b>，以下字段为冻结契约：",
         "<ul>",
         "  <li>{@code status}：free 空闲 / occupied 占用 / disabled 停用，推荐与仿真只消费 free；</li>",
         "  <li>{@code layer}：层号从 1 开始，层号越小越靠地面（评分 S_weight 依据）；</li>",
         "  <li>{@code x} / {@code y}：曼哈顿距离计算的起点（COM-6）；</li>",
         "  <li>{@code capacity}：体积口径，与 SKU 尺寸校验；</li>",
         "  <li>{@code warehouse_id}：冗余列，供 b、c 一次查出某仓库全部库位，无需 JOIN racks。</li>",
         "</ul>"],
        [
            ("id", "Long", "主键", "@TableId(type = IdType.AUTO)"),
            ("rack_id", "Long", "所属货架 id", None),
            ("warehouse_id", "Long", "所属仓库 id（冗余，由业务保证与货架一致）", None),
            ("code", "String", "库位唯一编码 巷道-货架序号-列-层，如 A-01-03-02，全局唯一", None),
            ("x", "Integer", "平面坐标 x（曼哈顿距离起点）", None),
            ("y", "Integer", "平面坐标 y（曼哈顿距离起点）", None),
            ("layer", "Integer", "层号，从 1 开始，层号越小越靠地面", None),
            ("status", "String", "状态：free 空闲 / occupied 占用 / disabled 停用", None),
            ("capacity", "BigDecimal", "库位容量（体积口径）", None),
            ("occupied_sku_id", "Long", "当前占用货物 id，status=occupied 时必须非空", None),
            ("created_at", "LocalDateTime", "创建时间(UTC)，插入时自动填充",
             '@TableField(value = "created_at", fill = FieldFill.INSERT)'),
            ("updated_at", "LocalDateTime", "更新时间(UTC)，插入与更新时自动填充",
             '@TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)'),
        ],
    ),
    (
        "skus",
        "Sku",
        ["货物（SKU）实体，对应表 {@code skus}。",
         "",
         "<p><b>核心输入契约</b>：{@code weight} / {@code turnoverRate} / {@code priority}",
         "是推荐引擎（b）评分与仿真引擎（c）选位的核心依据（《接口文档》第 6 节标注）。",
         "",
         "<p>{@code size} 为 JSON 列，反序列化为 {@code {\"length\":30,\"width\":20,\"height\":10}}，",
         "与《接口文档》API-030 请求示例完全一致。"],
        [
            ("id", "Long", "主键", "@TableId(type = IdType.AUTO)"),
            ("code", "String", "SKU 编码，全局唯一，如 SKU-001", None),
            ("name", "String", "货物名称", None),
            ("weight", "BigDecimal", "重量(kg)，评分 S_weight 输入", None),
            ("turnover_rate", "BigDecimal", "周转频次(次/单位时间)，评分 S_freq 输入", None),
            ("priority", "Integer", "出库优先级 1~5，越大越紧急，评分 S_priority 输入", None),
            ("category", "String", "品类，评分 S_other 分区匹配项", None),
            ("size", "SkuSize", "尺寸（JSON 列）",
             '@TableField(value = "size", typeHandler = JacksonTypeHandler.class)'),
            ("remark", "String", "备注", None),
            ("created_at", "LocalDateTime", "创建时间(UTC)，插入时自动填充",
             '@TableField(value = "created_at", fill = FieldFill.INSERT)'),
            ("updated_at", "LocalDateTime", "更新时间(UTC)，插入与更新时自动填充",
             '@TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)'),
        ],
    ),
    (
        "orders",
        "Order",
        ["出库订单实体，对应表 {@code orders}。",
         "",
         "<p>一条订单一条 SKU 明细（与《需求文档》6.1 一致）。",
         "出库仿真排序为「优先级降序 + 下达时间升序」（FR-4.1），",
         "对应索引 {@code idx_orders_status_priority_placed(status, priority DESC, placed_at ASC)}。"],
        [
            ("id", "Long", "主键", "@TableId(type = IdType.AUTO)"),
            ("order_no", "String", "订单号，全局唯一，如 SO-20260910-001", None),
            ("sku_id", "Long", "货物 id", None),
            ("quantity", "Integer", "出库数量", None),
            ("priority", "Integer", "订单优先级 1~5，越大越紧急", None),
            ("placed_at", "LocalDateTime", "下达时间(UTC)", None),
            ("status", "String",
             "状态：pending 待出库 / picking 拣选中 / completed 已完成 / cancelled 已取消", None),
            ("remark", "String", "备注", None),
            ("created_at", "LocalDateTime", "创建时间(UTC)，插入时自动填充",
             '@TableField(value = "created_at", fill = FieldFill.INSERT)'),
            ("updated_at", "LocalDateTime", "更新时间(UTC)，插入与更新时自动填充",
             '@TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)'),
        ],
    ),
    (
        "roles",
        "Role",
        ["角色实体，对应表 {@code roles}。",
         "",
         "<p>{@code is_builtin = 1} 的内置角色禁止删除（FR-RBAC-4），但其权限可被管理员调整。"],
        [
            ("id", "Long", "主键", "@TableId(type = IdType.AUTO)"),
            ("code", "String", "角色标识，全局唯一：admin/operator/analyst/viewer", None),
            ("name", "String", "角色名称（中文）", None),
            ("description", "String", "角色描述", None),
            ("is_builtin", "Integer", "是否内置角色：1 内置(禁删) / 0 自定义", None),
            ("created_at", "LocalDateTime", "创建时间(UTC)，插入时自动填充",
             '@TableField(value = "created_at", fill = FieldFill.INSERT)'),
            ("updated_at", "LocalDateTime", "更新时间(UTC)，插入与更新时自动填充",
             '@TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)'),
        ],
    ),
    (
        "permissions",
        "Permission",
        ["权限实体，对应表 {@code permissions}。",
         "",
         "<p>权限码必须来自《接口文档》1.4 清单，禁止自行发明（《代码规范》4.3）。"],
        [
            ("id", "Long", "主键", "@TableId(type = IdType.AUTO)"),
            ("code", "String", "权限码，全局唯一，如 user:manage", None),
            ("name", "String", "权限名称（中文）", None),
            ("type", "String", "权限类型：menu 菜单 / action 操作", None),
            ("sort_no", "Integer", "展示排序", None),
            ("remark", "String", "备注", None),
            ("created_at", "LocalDateTime", "创建时间(UTC)，插入时自动填充",
             '@TableField(value = "created_at", fill = FieldFill.INSERT)'),
            ("updated_at", "LocalDateTime", "更新时间(UTC)，插入与更新时自动填充",
             '@TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)'),
        ],
    ),
    (
        "user_roles",
        "UserRole",
        ["用户-角色关联实体，对应表 {@code user_roles}（多对多）。"],
        [
            ("id", "Long", "主键", "@TableId(type = IdType.AUTO)"),
            ("user_id", "Long", "用户 id", None),
            ("role_id", "Long", "角色 id", None),
            ("created_at", "LocalDateTime", "创建时间(UTC)，插入时自动填充",
             '@TableField(value = "created_at", fill = FieldFill.INSERT)'),
        ],
    ),
    (
        "role_permissions",
        "RolePermission",
        ["角色-权限关联实体，对应表 {@code role_permissions}（多对多）。",
         "",
         "<p>API-014 分配权限为「全量覆盖」语义：先删除该角色全部关联，再批量插入。"],
        [
            ("id", "Long", "主键", "@TableId(type = IdType.AUTO)"),
            ("role_id", "Long", "角色 id", None),
            ("permission_id", "Long", "权限 id", None),
            ("created_at", "LocalDateTime", "创建时间(UTC)，插入时自动填充",
             '@TableField(value = "created_at", fill = FieldFill.INSERT)'),
        ],
    ),
    (
        "audit_logs",
        "AuditLog",
        ["操作审计日志实体，对应表 {@code audit_logs}。",
         "",
         "<p>《代码规范》4.6：关键路径（登录/登出/越权等）记录操作人、资源、结果；",
         "禁止写入密码、Token 等敏感信息。"],
        [
            ("id", "Long", "主键", "@TableId(type = IdType.AUTO)"),
            ("user_id", "Long", "操作人 id", None),
            ("account", "String", "操作人账号快照（用户删除后仍可追溯）", None),
            ("action", "String", "行为：login/logout/create/update/delete/adopt/denied 等", None),
            ("resource", "String", "资源，如 skus/roles/locations", None),
            ("resource_id", "String", "资源 id", None),
            ("detail", "String", "详情 JSON 字符串（禁止写入密码、Token）", None),
            ("ip", "String", "客户端 IP（兼容 IPv6）", None),
            ("created_at", "LocalDateTime", "发生时间(UTC)，插入时自动填充",
             '@TableField(value = "created_at", fill = FieldFill.INSERT)'),
        ],
    ),
]

HEADER_IMPORTS = """package com.wms.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

"""

# 非 java.lang 的类型 → import 语句（按字典序插入，保证生成结果稳定）
EXTRA_IMPORTS = {
    "BigDecimal": "import java.math.BigDecimal;",
    "LocalDateTime": "import java.time.LocalDateTime;",
    "SkuSize": "import com.wms.domain.entity.type.SkuSize;",
}


def render_class(table: str, class_name: str, class_doc: list[str], fields: list[tuple]) -> str:
    """渲染一个实体类。"""
    used_types = {java_type for _, java_type, _, _ in fields}
    imports = {EXTRA_IMPORTS[name] for name in EXTRA_IMPORTS if name in used_types}
    # JSON 列需要 MyBatis-Plus 的 JacksonTypeHandler
    if any(annotation and "JacksonTypeHandler" in annotation for _, _, _, annotation in fields):
        imports.add("import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;")
    imports = sorted(imports)

    lines: list[str] = [HEADER_IMPORTS.rstrip("\n"), ""]
    lines.extend(imports)
    lines.append("")
    lines.append("/**")
    for line in class_doc:
        lines.append(f" * {line}".rstrip())
    lines.append(" *")
    lines.append(" * @author a")
    lines.append(" */")
    # 含 JSON 列（typeHandler）的实体必须 autoResultMap = true，否则查询结果无法反序列化
    has_json_column = any(annotation and "typeHandler" in annotation
                          for _, _, _, annotation in fields)
    table_annotation = f'@TableName(value = "{table}", autoResultMap = true)' if has_json_column \
        else f'@TableName("{table}")'
    lines.append(table_annotation)
    lines.append(f"public class {class_name} {{")
    lines.append("")

    for column, java_type, comment, annotation in fields:
        field = camel(column)
        if annotation:
            lines.append(f"    /** {comment}。 */")
            lines.append(f"    {annotation}")
        else:
            lines.append(f"    /** {comment}。 */")
        lines.append(f"    private {java_type} {field};")
        lines.append("")

    for column, java_type, comment, _ in fields:
        field = camel(column)
        cap = pascal(column)
        lines.append("    /**")
        lines.append(f"     * 获取{comment}。")
        lines.append("     *")
        lines.append(f"     * @return {comment}")
        lines.append("     */")
        lines.append(f"    public {java_type} get{cap}() {{")
        lines.append(f"        return {field};")
        lines.append("    }")
        lines.append("")
        lines.append("    /**")
        lines.append(f"     * 设置{comment}。")
        lines.append("     *")
        lines.append(f"     * @param {field} {comment}")
        lines.append("     */")
        lines.append(f"    public void set{cap}({java_type} {field}) {{")
        lines.append(f"        this.{field} = {field};")
        lines.append("    }")
        lines.append("")

    lines.append("}")
    return "\n".join(lines) + "\n"


def main() -> None:
    """生成全部实体类。"""
    ENTITY_DIR.mkdir(parents=True, exist_ok=True)
    for table, class_name, class_doc, fields in SCHEMA:
        content = render_class(table, class_name, class_doc, fields)
        target = ENTITY_DIR / f"{class_name}.java"
        target.write_text(content, encoding="utf-8", newline="\n")
        print(f"生成 {target.relative_to(BASE)}  ({len(fields)} 字段)")


if __name__ == "__main__":
    main()
