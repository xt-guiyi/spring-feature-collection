# PostgreSQL JSONB 商品扩展信息学习案例设计

## 1. 目标

在现有 PostgreSQL playground 中增加一套贴近真实业务的 JSONB 学习案例。关系型字段继续保存稳定、需要约束和关联的数据，`JSONB` 只保存不同商品之间结构不固定的扩展属性，让学习者理解 PostgreSQL 如何同时处理关系数据与半结构化数据。

案例必须同时提供普通 MyBatis 和官方 MyBatis-Plus 两套入口，接口后缀、请求参数、返回结构和异常语义保持一致。

## 1.1 方案比较与结论

- 直接给 `products` 增加 `attributes JSONB`：模型最直观，但会改变现有商品 CRUD、JOIN 和事务案例的实体及 SQL，学习主题之间容易互相干扰。
- 新增独立 `product_profiles` 表：通过 `product_id` 关联现有商品，JSONB 能力完整，同时保持旧功能稳定。本案例采用此方案。
- 只在 SQL 脚本中写几条 JSONB 查询：改动最小，但无法演示前端 JSON、TypeHandler、MyBatis XML 和 MyBatis-Plus Wrapper 的完整链路，不满足本次学习目标。

## 2. 业务模型

新增 `product_profiles` 表，不修改现有 `products` 表，避免 JSONB 学习功能影响已有商品 CRUD、JOIN、事务和统计案例。

表结构：

- `id BIGSERIAL PRIMARY KEY`：扩展记录主键。
- `product_id BIGINT NOT NULL UNIQUE`：逻辑关联 `products.id`，一个商品最多一条扩展信息。
- `attributes JSONB NOT NULL DEFAULT '{}'::jsonb`：保存品牌、颜色、规格、标签、质保配置等动态属性。
- `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`：创建时间。
- `updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`：最后更新时间。
- `CHECK (jsonb_typeof(attributes) = 'object')`：只允许 JSON 对象作为属性根节点。

保持项目当前“逻辑外键”的建模约定，不增加数据库外键；Service 在新增扩展记录前校验 `products` 中的商品真实存在。

## 3. 默认数据

`docs/schema-demo.sql` 为现有三个商品各准备一条扩展信息：

- iPhone：品牌、颜色、存储容量、网络类型、标签和质保对象。
- MacBook Pro：品牌、颜色、内存、存储、芯片、标签和质保对象。
- AirPods Pro：品牌、颜色、降噪、防水等级、标签和质保对象。

默认 JSON 同时包含字符串、数字、布尔值、数组和嵌套对象，使所有查询与更新操作都有可直接观察的数据。

## 4. 接口入口

两套入口分别为：

- 普通 MyBatis：`/api/playground/pg/mybatis/jsonb/product-profiles/**`
- MyBatis-Plus：`/api/playground/pg/mybatis-plus/jsonb/product-profiles/**`

采用独立的抽象 Controller 和 JSONB Service，不继续向已有 `AbstractPgDataAccessController`、`PgMyBatisServiceImpl`、`PgMyBatisPlusServiceImpl` 添加职责。

具体接口：

- `POST /product-profiles`：创建商品扩展信息，返回生成的主键。
- `GET /product-profiles/{id}`：查询一条扩展信息，`attributes` 直接作为 JSON 对象返回前端。
- `GET /product-profiles`：查询全部扩展信息并按主键升序排列。
- `POST /product-profiles/search`：按品牌、标签、属性是否存在和质保开关动态查询。
- `PUT /product-profiles/{id}/attributes`：使用 `||` 把请求 JSON 合并到顶层属性。
- `PUT /product-profiles/{id}/warranty-months`：使用 `jsonb_set` 局部更新 `warranty.months`，不覆盖其他 JSON 内容。
- `DELETE /product-profiles/{id}/attributes/{key}`：使用 `-` 删除一个顶层属性，不删除整条商品扩展记录。

不提供任意 JSONPath 字符串拼接接口，避免把未经校验的路径或 SQL 片段直接带入数据库。

## 5. 请求与返回模型

Java 使用 Jackson `JsonNode` 表达动态 JSON：

- `PgProductProfile.attributes` 类型为 `JsonNode`。
- 创建请求包含 `productId` 和 `attributes`。
- 搜索请求包含可选的 `brand`、`tag`、`requiredKey`、`warrantyEnabled`。
- 合并请求只包含非空 JSON 对象 `attributes`。
- 嵌套质保更新通过数值参数 `months` 表达，Service 校验其大于等于零。

Controller 继续使用项目现有 `Result<T>`；MongoDB、PostgreSQL JSONB 对外都表现为普通 JSON，前端不需要感知 `PGobject`。

## 6. JSONB 类型映射

新增专用 `PgJsonbTypeHandler`：

- 写入时将 `JsonNode` 序列化为字符串，包装为 PostgreSQL `PGobject`，类型设置为 `jsonb`。
- 读取时将 JDBC 返回的 JSON 字符串反序列化为 `JsonNode`。
- 序列化失败统一转为 `SQLException`，不静默保存错误 JSON。

实体使用 `@TableName(autoResultMap = true)` 和 `@TableField(typeHandler = PgJsonbTypeHandler.class)`，使 MyBatis-Plus 的 `BaseMapper` 能正确处理 JSONB。普通 MyBatis XML 的参数和 `resultMap` 显式声明同一个 TypeHandler，便于对照学习。

## 7. 两套数据访问实现

### 7.1 普通 MyBatis

Mapper XML 直接编写 PostgreSQL 原生 JSONB SQL：

- `attributes ->> 'brand'`：读取顶层文本。
- `attributes @> jsonb_build_object(...)`：判断标签数组或 JSON 结构是否被包含。
- `jsonb_exists(attributes, key)`：安全绑定动态 key；注释给出等价的 `attributes ? '固定key'`。
- `attributes #>> '{warranty,enabled}'`：读取嵌套文本并转换为布尔值。
- `jsonb_set` 配合 `COALESCE(attributes -> 'warranty', '{}'::jsonb)`：保留已有质保字段；当 `warranty` 父对象不存在时先补空对象，再写入 `months`。
- `attributes || patch`：合并顶层 JSON 对象。
- `attributes - key`：删除顶层属性。

### 7.2 MyBatis-Plus

- 新增、按 ID 查询和列表查询使用官方 `BaseMapper`。
- JSONB 动态查询使用 `QueryWrapper.apply`，值通过 `{0}`、`{1}` 参数占位绑定，禁止直接拼接用户输入。
- JSONB 合并、局部更新和属性删除使用 `UpdateWrapper.setSql` 的参数占位能力。
- PostgreSQL 运算符仍由数据库执行；注释明确 `QueryWrapper` 只负责安全组织 SQL，不会把 JSONB 运算转换成 Java 内存计算。

## 8. 索引

新增两个互补索引：

- `GIN (attributes)`：服务于 `@>`、键存在等 JSONB 查询。
- `BTREE ((attributes ->> 'brand'))`：服务于经常按品牌等值筛选的表达式查询。

学习文档说明：GIN 不是所有 JSONB 条件都自动受益，表达式、查询运算符和索引定义必须匹配；不要因为字段是 JSONB 就无条件创建大量索引。

## 9. 业务校验与错误处理

- `productId` 必须大于零且商品必须存在。
- 同一商品重复创建扩展信息时，由 `product_id` 唯一约束最终兜底，Service 转换为业务异常。
- `attributes` 和合并补丁必须是 JSON 对象，不能是数组、字符串或 `null`。
- 搜索条件全部为空时允许返回全部记录，并保持稳定排序。
- 删除属性的 SQL 同时要求目标 key 存在；影响行数为零时重新按 ID 查询，区分记录不存在和属性不存在。
- 删除属性 key 必须是非空白字符串；不允许删除整个 `attributes` 字段。
- 质保月数必须大于等于零。

## 10. 学习文档与注释

新增 JSONB 学习说明，包含：

- 为什么固定字段用普通列、动态字段才使用 JSONB。
- `JSON` 与 `JSONB` 的核心差异。
- 每个 Java 调用对应的 SQL 和请求示例。
- `->`、`->>`、`#>`、`#>>` 的返回类型差异。
- `@>`、键存在、`jsonb_set`、`||`、`-` 的逐层解释。
- GIN 与表达式索引的适用范围。
- 与 MySQL JSON 能力的谨慎比较，不笼统宣称 PostgreSQL 在所有场景都更快。

比较注释统一使用以下口径：

- `@>`、`?`、`#>>`、`jsonb_set` 和 `USING GIN` 是 PostgreSQL 的具体语法，不能原样复制到 MySQL。
- MySQL 8.4 也提供 `->`、`->>`、`JSON_CONTAINS`、`JSON_CONTAINS_PATH`、`JSON_SET`、`JSON_REMOVE` 等能力，因此不能把“语法不同”写成“MySQL 无法处理 JSON”。
- PostgreSQL 可以直接为完整 `jsonb` 建 GIN 索引并支持对应的包含、键存在操作符；MySQL InnoDB 没有 PostgreSQL GIN，但可以使用函数索引、生成列索引和面向 JSON 数组的多值索引。注释只说明索引模型和适用运算符不同，不绝对宣称某一数据库在所有 JSON 查询中更快。

复杂 Service 方法先写完整实现步骤，再按“第1步、第2步……”实现；XML SQL 前写学习型中文注释和等价 SQL 语义。

## 11. 约束与静态验收

- 不引入第三方 JSON 库，继续使用项目已有 Jackson。
- 不引入第三方 MyBatis-Plus JOIN 或 JSON 扩展。
- 不修改现有五张表及已有接口行为。
- 不编写或运行测试，不运行 Maven 构建；由用户自行运行验证。
- 只进行包结构、Mapper/XML 对应关系、类型引用、SQL 占位符、注释和格式的静态检查。
- 保留当前脏工作区及所有无关改动，不提交、不清理其他文件。
