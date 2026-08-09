# PostgreSQL JSONB 商品扩展信息学习说明

## 1. 为什么这里使用 JSONB

`products` 表中的商品名称、价格、库存属于结构稳定、需要约束、排序和计算的核心字段，因此继续使用普通关系列。
不同商品的扩展规格不一致：手机有网络类型，电脑有芯片和内存，耳机有降噪和防水等级。这些动态属性放在
`product_profiles.attributes JSONB` 中更合适。

```text
products（稳定关系字段）              product_profiles（动态扩展字段）
┌─────────────────────┐              ┌────────────────────────────┐
│ id / name           │◀─product_id─│ attributes JSONB           │
│ price / stock       │              │ brand / tags / warranty... │
└─────────────────────┘              └────────────────────────────┘
```

不要因为 JSONB 灵活就把所有数据都放进去。价格、库存、状态、关联 ID 等经常参与约束、JOIN、范围查询和计算的字段，
通常仍应使用普通列。

## 2. JSON 和 JSONB 的区别

PostgreSQL `json` 基本保留输入文本；`jsonb` 写入时会转换为可处理的二进制结构，不保留无意义空白和对象 key 的
原始顺序。`jsonb` 通常更适合查询、包含判断和索引，因此本案例使用 JSONB。

官方说明：<https://www.postgresql.org/docs/current/datatype-json.html>

MySQL 也有原生 `JSON` 类型，而且使用内部二进制格式。这里选择 PostgreSQL JSONB，不代表 MySQL 只能把 JSON
当普通字符串保存。

## 3. 表结构和默认数据

```sql
CREATE TABLE product_profiles (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (jsonb_typeof(attributes) = 'object')
);
```

默认数据位于 `docs/schema-demo.sql`，包含三种商品：

```json
{
  "brand": "Apple",
  "color": "黑色",
  "storageGb": 256,
  "network": "5G",
  "tags": ["手机", "5G", "iOS"],
  "warranty": {
    "enabled": true,
    "months": 12
  }
}
```

这个 JSON 同时包含：

- 字符串：`brand`、`color`；
- 数字：`storageGb`；
- 数组：`tags`；
- 布尔值：`warranty.enabled`；
- 嵌套对象：`warranty`。

## 4. 两套接口

普通 MyBatis 前缀：

```text
/api/playground/pg/mybatis/jsonb
```

MyBatis-Plus 前缀：

```text
/api/playground/pg/mybatis-plus/jsonb
```

下面示例使用 MyBatis 前缀；替换成 MyBatis-Plus 前缀即可观察另一套实现。

### 4.1 创建商品扩展信息

```http
POST /api/playground/pg/mybatis/jsonb/product-profiles
Content-Type: application/json
```

```json
{
  "productId": 4,
  "attributes": {
    "brand": "Sony",
    "color": "黑色",
    "tags": ["相机", "旅行"],
    "warranty": {
      "enabled": true,
      "months": 24
    }
  }
}
```

`attributes` 必须是 JSON 对象。数组、字符串、数字和 `null` 不能作为根节点，但可以作为对象内部的属性值。

### 4.2 查询

```http
GET /api/playground/pg/mybatis/jsonb/product-profiles/1
GET /api/playground/pg/mybatis/jsonb/product-profiles
```

响应中的 `attributes` 就是普通 JSON：

```json
{
  "id": 1,
  "productId": 1,
  "attributes": {
    "brand": "Apple",
    "tags": ["手机", "5G", "iOS"]
  },
  "createdAt": "2026-08-09T10:00:00",
  "updatedAt": "2026-08-09T10:00:00"
}
```

Java 使用 `JsonNode`，Jackson 会把它自然地序列化成 JSON 对象，前端不会看到 PostgreSQL JDBC 的
`PGobject`。

### 4.3 动态搜索

```http
POST /api/playground/pg/mybatis/jsonb/product-profiles/search
Content-Type: application/json
```

```json
{
  "brand": "Apple",
  "tag": "降噪",
  "requiredKey": "warranty",
  "warrantyEnabled": true
}
```

所有条件都可以省略；多个非空条件之间使用 `AND`。

### 4.4 合并顶层属性

```http
PUT /api/playground/pg/mybatis/jsonb/product-profiles/1/attributes
Content-Type: application/json
```

```json
{
  "attributes": {
    "color": "蓝色",
    "promotion": true
  }
}
```

原数据：

```json
{"brand":"Apple","color":"黑色","storageGb":256}
```

执行 `||` 后：

```json
{"brand":"Apple","color":"蓝色","storageGb":256,"promotion":true}
```

同名的 `color` 被右侧覆盖，其他属性保留。

### 4.5 局部修改嵌套质保月份

```http
PUT /api/playground/pg/mybatis/jsonb/product-profiles/1/warranty-months?months=36
```

只修改：

```text
warranty.months
```

`warranty.enabled` 及 JSON 中的其他内容不会被覆盖。

### 4.6 删除顶层属性

```http
DELETE /api/playground/pg/mybatis/jsonb/product-profiles/1/attributes/promotion
```

只删除 `promotion`，不会删除整条 `product_profiles` 记录。

## 5. 查询运算符逐个理解

PostgreSQL JSON 函数与运算符官方说明：
<https://www.postgresql.org/docs/current/functions-json.html>

### 5.1 `->` 与 `->>`

```sql
attributes -> 'warranty'
```

返回 JSONB：

```json
{"enabled":true,"months":12}
```

```sql
attributes ->> 'brand'
```

返回 SQL `text`：

```text
Apple
```

记忆方式：

```text
->   保持 JSON/JSONB 类型
->>  取出文本
```

MySQL 也有 `->` 和 `->>`，但路径通常写成 `$.brand`：

```sql
attributes ->> '$.brand'
```

所以当前 PostgreSQL 写法不能原样复制，但不能说 MySQL 做不了字段提取。

### 5.2 `#>` 与 `#>>`

```sql
attributes #> '{warranty,enabled}'
```

返回 JSONB 布尔值。

```sql
attributes #>> '{warranty,enabled}'
```

返回文本 `true`，本案例再转换成 SQL boolean：

```sql
(attributes #>> '{warranty,enabled}')::boolean
```

`#>`、`#>>` 的 PostgreSQL `text[]` 路径语法不能原样用于 MySQL；MySQL 可使用 `JSON_EXTRACT` 或
`->>` 配合 `$.warranty.enabled` 路径实现相同目标。

### 5.3 `@>` 包含判断

```sql
attributes @> '{"tags":["降噪"]}'::jsonb
```

含义是：左侧 JSONB 是否包含右侧指定结构。

MySQL 没有相同的 `@>` 写法，但提供：

```sql
JSON_CONTAINS(attributes, '{"tags":["降噪"]}')
```

### 5.4 `?` 与 `jsonb_exists`

固定 key 可以写：

```sql
attributes ? 'warranty'
```

动态 key 在本项目中使用函数形式，便于参数绑定：

```sql
jsonb_exists(attributes, #{requiredKey})
```

MySQL 可使用：

```sql
JSON_CONTAINS_PATH(attributes, 'one', '$.warranty')
```

### 5.5 `jsonb_set`

PostgreSQL：

```sql
jsonb_set(attributes, '{warranty,months}', '24'::jsonb, true)
```

MySQL 写法不同：

```sql
JSON_SET(attributes, '$.warranty.months', 24)
```

本案例的 SQL 比这个基础示例多一步：先保留已有 `warranty` 对象，再合并 `months`。原因是 PostgreSQL
`jsonb_set` 在中间父路径不存在时不会自动补齐所有父对象。如果历史数据中的 `warranty` 不是对象，案例 SQL
会把它按空对象处理后再写入合法的 `months` 属性。

### 5.6 `||` 与 `-`

PostgreSQL 顶层对象合并：

```sql
attributes || '{"color":"蓝色"}'::jsonb
```

MySQL 可使用 `JSON_MERGE_PATCH` 实现接近的对象补丁语义。

PostgreSQL 删除顶层 key：

```sql
attributes - 'promotion'
```

MySQL 可使用：

```sql
JSON_REMOVE(attributes, '$.promotion')
```

## 6. TypeHandler 数据流

写入流程：

```text
前端 JSON
  ↓ Jackson
JsonNode
  ↓ PgJsonbTypeHandler
PGobject(type=jsonb, value=JSON文本)
  ↓ PostgreSQL JDBC
JSONB 列
```

读取流程相反：

```text
JSONB 列
  ↓ JDBC JSON文本
PgJsonbTypeHandler
  ↓ Jackson
JsonNode
  ↓ Controller响应
前端 JSON对象
```

`PGobject` 是 PostgreSQL JDBC 类型，不能原样用于 MySQL JDBC。这是驱动层差异，不是 MySQL 不支持 JSON。

## 7. MyBatis 和 MyBatis-Plus 的差别

普通 MyBatis 在 XML 中直接写：

```sql
attributes @> jsonb_build_object('tags', jsonb_build_array(#{tag}))
```

MyBatis-Plus 官方核心没有专门的 PostgreSQL JSONB Lambda API，因此使用：

```java
wrapper.apply(
    "attributes @> CAST({0} AS jsonb)",
    tagContainmentJson
);
```

`{0}` 最终仍然是绑定参数，不是把用户输入拼进 SQL。JSONB 运算仍由 PostgreSQL 完成，Wrapper 只负责组织 SQL。

## 8. 索引

### 8.1 GIN

```sql
CREATE INDEX idx_product_profiles_attributes_gin
ON product_profiles USING GIN (attributes);
```

它适合受支持的包含和键存在查询，例如 `@>`。不是所有 JSON 表达式都会自动使用这个索引。

### 8.2 品牌表达式索引

```sql
CREATE INDEX idx_product_profiles_brand
ON product_profiles ((attributes ->> 'brand'));
```

它与下面的条件表达式直接对应：

```sql
WHERE attributes ->> 'brand' = 'Apple'
```

可以使用 `EXPLAIN` 观察执行计划：

```sql
EXPLAIN
SELECT *
FROM product_profiles
WHERE attributes @> '{"tags":["降噪"]}'::jsonb;
```

默认数据只有三行，优化器很可能认为顺序扫描更便宜。是否使用索引与数据量、选择性、统计信息和成本估算有关，
不能因为小样例显示 `Seq Scan` 就断定索引无效。

### 8.3 与 MySQL 索引的准确比较

MySQL InnoDB 没有 PostgreSQL 的 `USING GIN`。MySQL 8.4 可以使用：

- 函数索引；
- 生成列再建立普通索引；
- 面向 JSON 数组的多值索引。

因此准确表述是“索引模型、支持的查询表达式和建法不同”，不是“MySQL JSON 不能建索引”。

MySQL 8.4 JSON 函数：
<https://dev.mysql.com/doc/refman/8.4/en/json-function-reference.html>

MySQL 8.4 函数索引和多值索引：
<https://dev.mysql.com/doc/refman/8.4/en/create-index.html>

## 9. PostgreSQL 相对突出的地方

本案例中 PostgreSQL 的优势主要体现在：

- `jsonb` 拥有紧凑且可组合的 `@>`、`?`、`#>>` 等操作符体系；
- 可以直接为完整 JSONB 建 GIN 索引；
- JSONB、数组、表达式索引、SQL/JSON、关系查询可以在同一 SQL 中自然组合；
- JSONB 更新仍处于普通 PostgreSQL 事务、锁和 MVCC 体系中。

但是否选择 PostgreSQL 不能只看 JSON 功能，还要结合团队技术栈、事务需求、运维能力、查询模型和真实执行计划。
