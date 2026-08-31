# Flyway 数据库迁移模块

本模块用一个很小的客户账户案例演示 Flyway 的版本化迁移：应用启动时，Flyway 通过标记了 `@FlywayDataSource` 的 `playgroundDataSource` 连接 `demo` 数据库，按 `V1`、`V2`、`V3`、`V4` 顺序执行 `classpath:db/migration/playground` 下的 SQL。

## Docker 初始化和 Flyway 的边界

| 机制 | 负责什么 | 本模块是否使用 |
| --- | --- | --- |
| Docker `/docker-entrypoint-initdb.d` | PostgreSQL 空数据卷第一次初始化时创建基础学习表 | 不修改现有 init SQL |
| Flyway | 应用启动后的版本化 DDL、数据回填和迁移历史 | 只使用 `playgroundDataSource` |

迁移对象全部放在 `flyway_migration` schema 中，既不接管 `public`，也不修改 Flowable、XXL-JOB 或其他已有表。Flyway 会根据配置自动创建该 schema 和 `flyway_schema_history`；已有 PostgreSQL 数据卷不需要重建。

## 启动流程

```text
启动 Spring Boot
       │
       ▼
Flyway 连接 playgroundDataSource（demo）
       │
       ├─ 创建 flyway_migration schema（不存在时）
       ├─ 读取 flyway_migration.flyway_schema_history
       ├─ 执行待执行的 V1 → V2 → V3 → V4
       └─ 记录版本、checksum、耗时和执行人
       │
       ▼
应用继续启动，提供只读迁移查询接口
```

## 版本演进

| 版本 | 脚本 | 变更 | 学习重点 |
| --- | --- | --- | --- |
| V1 | `V1__create_customer_account.sql` | 创建 `flyway_migration.customer_account` 基础字段，并插入 3 行演示数据 | 初始表结构与种子数据 |
| V2 | `V2__add_customer_level.sql` | 增加允许 `NULL` 的 `customer_level` 字段和索引 | 先扩展、后收紧，兼容旧数据 |
| V3 | `V3__backfill_customer_level.sql` | 按 `total_spent` 回填等级，设置默认值、`NOT NULL` 和合法值检查约束 | 数据回填与结构约束分步发布 |
| V4 | `V4__add_customer_gender.sql` | 为 `flyway_migration.customer_account` 增加非空 `gender` 字段，默认值为 `男` | 为已有表增加带默认值的字段 |
| V5 | `V5__add_user_gender.sql` | 删除 V4 误加在 `customer_account` 上的 `gender`，并为 `public.users` 增加非空 `gender` 字段，默认值为 `男` | 用后续版本修正已执行迁移 |

V1 的三条固定金额用于覆盖边界：`30000` → `VIP`，`10000` → `GOLD`，`5000` → `STANDARD`。因此每次在全新数据库执行完整迁移后，应看到三行且等级分别为这三个值。

## 查询接口

接口是只读的，不提供 `migrate`、`repair`、`clean`、`undo` 等写操作。

```bash
curl http://localhost:4379/api/playground/migration/status
curl http://localhost:4379/api/playground/migration/history
```

`status` 会展示 `schema`、完整的 `historyTable`（例如 `flyway_migration.flyway_schema_history`）、当前版本和描述、已执行/待执行/失败数量，以及 `valid` 和 `validationMessage`。`history` 会展示每个迁移的版本、脚本、状态、checksum、安装时间、执行人和耗时等字段。返回结果沿用项目的 `Result` 外层结构。

## 用 SQL 观察迁移结果

使用连接到 `demo` 数据库的 `psql` 或其他客户端执行：

```sql
SELECT installed_rank,
       version,
       description,
       type,
       success,
       script,
       checksum,
       installed_on,
       installed_by,
       execution_time,
       success AS applied
FROM flyway_migration.flyway_schema_history
ORDER BY installed_rank;

SELECT id,
       customer_code,
       customer_name,
       total_spent,
       customer_level,
       created_at
FROM flyway_migration.customer_account
ORDER BY id;

SELECT id,
       username,
       gender
FROM public.users
ORDER BY id;
```

预期历史表有 V1、V2、V3、V4、V5 五条成功记录，客户表有三行，等级为 `VIP`、`GOLD`、`STANDARD`；`public.users` 的现有用户性别默认为 `男`。重启应用时 Flyway 会复用历史记录，不会重复插入客户数据。

## 修改迁移脚本的规则

已经执行过的迁移脚本不能直接修改：Flyway 会比较 checksum，并在校验接口或下次启动时报告不一致。需要修正结构时，保留旧脚本，新增更高版本（例如 `V6__...sql`）承载变更；不要通过 `clean` 绕过问题。当前配置还禁用了 `clean`，以保护学习数据库中的已有数据。

本模块的迁移失败会阻止应用正常启动，便于在学习阶段尽早发现 SQL 或 schema 问题。
