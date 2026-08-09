# PostgreSQL 双数据访问实现设计

## 1. 目标

基于 `users`、`id_cards`、`orders`、`products`、`order_products` 五张表，提供两套行为一致、入口清晰的数据访问学习案例：

- 普通 MyBatis：通过 Mapper 接口和 XML 编写 SQL。
- MyBatis-Plus：只使用官方 `BaseMapper`、Wrapper 和分页插件，不引入第三方 JOIN 扩展。

同一个业务场景必须在两套入口中各实现一次，并保持请求参数、响应结构、排序规则和异常语义一致。案例从基础 CRUD 逐步覆盖条件、分页、批量、关联、聚合、子查询、动态 SQL、PostgreSQL 高级查询和事务。

## 2. 约束

- 不引入 `mybatis-plus-join` 或其他第三方 JOIN 扩展。
- MyBatis 使用数据库原生 SQL 完成复杂查询。
- MyBatis-Plus 对单表操作使用官方 API；多表和高级查询通过多个 `BaseMapper` 查询后在 Service 层组装等价结果。
- 不把 MyBatis-Plus 自定义 XML SQL 伪装成 MyBatis-Plus 原生能力。
- 所有事务显式绑定 `playgroundTransactionManager`。
- 本次不编写或运行测试，只进行静态检查，测试由用户完成。
- 保留用户工作区中与本功能无关的未提交改动。

## 3. 总体架构

采用平行双模块、共享数据模型的结构。

### 3.1 接口入口

- `/api/playground/pg/mybatis/**`
- `/api/playground/pg/mybatis-plus/**`

两套入口使用相同的后缀。例如创建用户分别为：

- `POST /api/playground/pg/mybatis/users`
- `POST /api/playground/pg/mybatis-plus/users`

### 3.2 代码分层

- `PgMyBatisController`：普通 MyBatis 学习入口。
- `PgMyBatisPlusController`：MyBatis-Plus 学习入口。
- `PgMyBatisService`、`PgMyBatisServiceImpl`：组织 XML Mapper 调用。
- `PgMyBatisPlusService`、`PgMyBatisPlusServiceImpl`：组织 BaseMapper、Wrapper、分页和 Java 结果组装。
- MyBatis Mapper 按用户、订单、商品和综合查询拆分，避免单个 XML 过大。
- MyBatis-Plus 为五张表分别提供一个 `BaseMapper`。
- Entity、请求 DTO、响应 VO 和分页响应由两套实现共享。

当前混合两种技术的 `PgBusinessController`、`PgBusinessService`、`PgBusinessServiceImpl` 和 `PgComplexMapper` 将由上述平行结构取代，避免一个方法名无法体现具体实现技术。

## 4. 基础 CRUD

五张表均提供完整的创建、单条查询、列表查询、更新和删除能力：

| 资源 | 表 | 路径 |
| --- | --- | --- |
| 用户 | `users` | `/users` |
| 身份证 | `id_cards` | `/id-cards` |
| 订单 | `orders` | `/orders` |
| 商品 | `products` | `/products` |
| 订单商品 | `order_products` | `/order-products` |

每个资源遵循同一组接口：

- `POST /资源`：创建并返回生成的主键。
- `GET /资源/{id}`：按主键查询。
- `GET /资源`：查询全部并按主键升序返回。
- `PUT /资源/{id}`：按主键更新非空字段。
- `DELETE /资源/{id}`：按主键删除。

普通 MyBatis 在 XML 中分别演示 `INSERT`、`SELECT`、`UPDATE`、`DELETE` 和 PostgreSQL `RETURNING id`。MyBatis-Plus 分别演示 `insert`、`selectById`、`selectList`、`updateById` 和 `deleteById`。

## 5. 单表常用 SQL

两套实现提供以下成对案例：

- 用户名和邮箱模糊查询：`LIKE` 对比 `LambdaQueryWrapper.like`。
- 用户状态、订单状态精确查询：`=` 对比 Wrapper `eq`。
- ID 集合查询：`IN` 和 MyBatis `<foreach>` 对比 Wrapper `in`。
- 订单金额、商品价格范围：`BETWEEN` 对比 Wrapper `between`。
- 手机号空值查询：`IS NULL` 对比 Wrapper `isNull`。
- 多字段排序：`ORDER BY` 对比 Wrapper `orderByAsc/orderByDesc`。
- 用户和订单分页：MyBatis `COUNT + LIMIT/OFFSET` 对比 MyBatis-Plus 分页插件。
- 批量新增、批量更新状态、批量删除：XML `<foreach>` 对比事务内多次 BaseMapper 操作。

所有可选查询条件均使用请求 DTO 表达，避免 Controller 出现过长参数列表。

## 6. JOIN 案例

### 6.1 INNER JOIN

查询订单、用户、订单明细和商品，返回订单商品详情。MyBatis 使用四表 `INNER JOIN`，只保留关联完整的数据。MyBatis-Plus 分别批量查询四张表，以主键和逻辑外键建立 Map，再保留四边均匹配的数据。

### 6.2 LEFT JOIN

查询所有用户及身份证信息，保留未绑定身份证的用户。MyBatis 使用 `users LEFT JOIN id_cards`。MyBatis-Plus 以完整用户列表为主，缺失身份证时保留用户并将身份证字段置空。

### 6.3 RIGHT JOIN

查询所有用户及订单，保留没有订单的用户。MyBatis 使用 `orders RIGHT JOIN users`，用于学习右连接的保留方向。MyBatis-Plus 以完整用户列表为主进行组装。注释中说明交换表顺序后的 `LEFT JOIN` 可得到等价结果，实际业务通常更偏向左连接写法。

### 6.4 FULL OUTER JOIN

对商品与订单商品关联记录做完整审计，同时展示未售出的商品以及引用不到商品的关联记录。MyBatis 使用 `products FULL OUTER JOIN order_products`。MyBatis-Plus 合并两边全部关联键，任一侧缺失时保留记录并补空字段。

### 6.5 CROSS JOIN

生成用户与商品的推荐候选组合。MyBatis 使用 `CROSS JOIN` 并通过请求参数限制返回数量。MyBatis-Plus 对两个列表生成笛卡尔积并应用相同上限，防止数据量乘积失控。

### 6.6 LATERAL JOIN

查询每个用户最新一笔订单。MyBatis 使用 PostgreSQL `LEFT JOIN LATERAL` 和 `ORDER BY ... LIMIT 1`。MyBatis-Plus 一次性批量查询订单后按用户分组，选择创建时间最新的订单，避免逐个用户查询造成 N+1 问题。

## 7. 复杂 SQL 与等价业务实现

| 场景 | MyBatis | MyBatis-Plus |
| --- | --- | --- |
| 动态订单查询 | `<where>`、`<if>` | 条件化 `LambdaQueryWrapper` |
| 用户订单统计 | `GROUP BY`、`COUNT`、`SUM` | 批量查询后 Java 分组聚合 |
| 消费排行 | 聚合子查询、`ORDER BY`、`LIMIT` | Java 聚合、排序、截断 |
| 有订单的用户 | `EXISTS` | 订单用户 ID 集合加 Wrapper `in` |
| 无订单的用户 | `NOT EXISTS` | 用户全集减去订单用户集合 |
| 订单数过滤 | `GROUP BY ... HAVING` | Java 分组计数后过滤 |
| 结果集合并 | `UNION ALL` | 两次查询后按 UNION ALL 语义拼接 |
| 状态名称 | `CASE WHEN` | Java 状态映射 |
| 枚举自动映射 | XML 查询结果映射为带枚举字段的投影对象 | `BaseMapper` 查询带 `@EnumValue` 枚举字段的投影对象 |
| 空值默认值 | `COALESCE` | Java 空值处理 |
| 日期统计 | PostgreSQL 日期函数 | Java 时间 API |
| 消费排名 | 窗口函数 `ROW_NUMBER` | Java 排序后生成名次 |
| 分层统计 | PostgreSQL CTE | 分阶段查询与 Java 组合 |

普通 MyBatis 体现数据库一次完成复杂计算的能力；MyBatis-Plus 体现官方单表能力和服务层编排方式。每个案例注释说明 SQL 次数、数据传输量、可读性及性能差异。

### 7.1 订单状态枚举自动映射案例

- 新增独立接口 `/queries/order-status-enums`，由 MyBatis 和 MyBatis-Plus 两套前缀分别暴露。
- 数据库继续存储 `PENDING`、`PAID`、`CANCELLED` 状态码，不修改表结构。
- 专用查询投影的 `status` 字段使用枚举类型，枚举的 `code` 字段通过 `@EnumValue` 声明为数据库值。
- Mapper 查询完成后，Service 从枚举读取 `code` 和 `text`，仍以扁平结构返回 `status`、`statusName`。
- 保留原有 `CASE WHEN` 和 Java `statusName` 案例，让学习者直接对比 SQL 映射、手工映射与枚举映射。

## 8. 事务案例

提供“创建完整订单”成对接口，事务包含：

1. 校验用户存在。
2. 批量查询商品并校验库存。
3. 创建订单。
4. 创建多条订单商品记录。
5. 扣减商品库存。
6. 任一步骤失败时整体回滚。

MyBatis 使用 XML Mapper 完成写操作，MyBatis-Plus 使用五个官方 BaseMapper 完成写操作。两套 Service 均使用：

```java
@Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
```

库存更新必须在 SQL 条件中包含“库存大于等于扣减量”，通过受影响行数判断是否扣减成功，避免仅在 Java 中先查后改造成明显的超卖窗口。

## 9. 请求、响应和错误处理

- Controller 使用请求 DTO，不直接将持久化实体作为复杂写接口的请求体。
- 两套实现共享相同 DTO、VO 和 `PageResult`，确保响应可直接比较。
- 查询单条记录不存在时抛出 `BusinessException`。
- 创建订单时，用户不存在、商品不存在、数量非法或库存不足均给出明确业务异常。
- 空批次不下发无效 SQL，直接返回明确结果或抛出参数异常。
- 对金额范围、分页参数、查询数量上限进行边界校验。
- 数据库唯一约束负责最终一致性，Service 提供易理解的业务提示。

## 10. 学习型注释规范

- Controller 类和方法写中文 Javadoc，说明接口用途、请求示例方向以及对应的数据访问方式。
- Service 方法注释解释业务步骤、事务边界，以及 MyBatis 与 MyBatis-Plus 实现的核心差异。
- Mapper 接口注释解释参数、返回值和对应 SQL 知识点。
- XML 中每段 SQL 前写注释，说明驱动表、连接条件、保留哪一侧数据、动态标签作用、聚合阶段和排序逻辑。
- 对 `RIGHT JOIN`、`FULL OUTER JOIN`、`LATERAL JOIN`、窗口函数、CTE 等不常见语法补充适用场景和注意事项。
- MyBatis-Plus Wrapper 链式调用按条件分段，并说明生成的关键 SQL 条件。
- Java 组装复杂结果时解释如何保持 JOIN、聚合、排序和空值语义一致。
- 对可能产生大结果集的 `CROSS JOIN`、全量内存聚合和批量操作标注性能风险。
- 不为简单赋值、getter/setter 或显而易见的代码添加重复注释。

## 11. 配置调整

- `PlaygroundMyBatisConfig` 显式加载 `classpath*:mapper/**/*.xml`，确保自定义 `SqlSessionFactory` 能找到拆分后的 XML Mapper。
- 保留 PostgreSQL 分页插件配置。
- Mapper 扫描范围覆盖普通 MyBatis Mapper 和 MyBatis-Plus BaseMapper。
- 不修改业务数据源配置，不影响 Redis 和其他 playground 功能。

## 12. 静态验收标准

- 两套 Controller 的业务接口一一对应，仅 URL 前缀不同。
- 五张表均具备两套完整 CRUD。
- 单表、批量、动态、关联、聚合、子查询、函数、高级查询和事务场景均有成对实现。
- 六种连接案例均存在：`INNER`、`LEFT`、`RIGHT`、`FULL OUTER`、`CROSS`、`LATERAL`。
- Mapper 接口方法与 XML statement ID、参数名、返回类型一致。
- MyBatis-Plus 复杂场景不依赖第三方 JOIN 库，也不通过自定义 XML 冒充官方 Plus 能力。
- 旧的混合入口和无用导入被清理，没有残留调用。
- 新增核心类和 SQL 均包含面向学习的中文注释。
- 不运行测试或构建命令，由用户自行验证运行效果。
