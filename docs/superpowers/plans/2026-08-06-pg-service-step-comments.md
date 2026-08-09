# PostgreSQL Service 复杂方法步骤化注释 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 PostgreSQL 双数据访问学习模块的复杂 Service 方法增加“完整实现步骤 + 对应代码阶段”注释，使读者先理解整体路线，再逐段阅读实现。

**Architecture:** 仅重构注释，不修改任何 Java 语句。MyBatis-Plus 侧覆盖动态 Wrapper、分页、批量、六种 JOIN、Java 聚合和完整下单；普通 MyBatis 侧只覆盖分页、批量、枚举转换和完整下单等真正承担多阶段编排的方法。

**Tech Stack:** Java 21、Spring Boot 4.1、MyBatis、MyBatis-Plus 3.5.17、PostgreSQL。

## Global Constraints

- 只修改 `PgMyBatisPlusServiceImpl` 和 `PgMyBatisServiceImpl` 中的注释，不改变业务语句。
- 每个复杂方法开头先写完整的 `实现步骤` 清单，再用 `// 第N步：` 标记对应代码阶段。
- 总步骤与代码阶段编号一一对应，不缺号、不重号。
- 保留 JOIN 语义、N+1、原子扣库存、事务回滚和死锁顺序等有效学习注释。
- 简单 CRUD 和单次 Mapper 转发保持简洁。
- 不运行测试和构建，只执行静态差异、格式和编号检查。
- 不提交 Git commit，保留工作区中全部无关改动。

---

### Task 1: MyBatis-Plus 动态查询、分页和批量方法

**Files:**
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisPlusServiceImpl.java:280-390`

**Interfaces:**
- Consumes: 现有 Wrapper、分页和批量方法实现。
- Produces: 方法开头的完整步骤清单，以及与代码块一一对应的阶段注释。

- [x] **Step 1: 为动态用户查询补三阶段结构**

在 `searchUsers` 开头写明：

```text
1. 将空请求转换为空查询对象；
2. 按关键字、状态、ID集合和手机号空值条件构造 Wrapper；
3. 添加稳定排序并执行查询。
```

将现有 `and` 括号语义说明放入第2步代码段。

- [x] **Step 2: 为用户和订单分页补三阶段结构**

`pageUsers`、`pageOrders` 分别使用：

```text
1. 校验页码和每页数量；
2. 创建 Page 对象并执行带稳定排序的分页查询；
3. 转换为共享 PageResult。
```

- [x] **Step 3: 为三种用户批量写操作补步骤结构**

`batchCreateUsers` 写明“校验批次 → 逐个校验并插入 → 全部成功返回”；
`batchUpdateUserStatus` 写明“校验ID与状态 → 构造 IN 更新 → 检查影响行数”；
`batchDeleteUsers` 写明“校验ID集合 → 批量删除 → 根据影响行数返回”。

- [x] **Step 4: 为动态订单和商品查询补步骤结构**

`searchOrders` 写明“规范化并校验金额范围 → 构造条件 Wrapper → 查询后统一 NULLS LAST 排序”；
`searchProducts` 写明“规范化并校验价格范围 → 判断 BETWEEN 或单边界 → 构造条件与排序并查询”。

- [x] **Step 5: 为条件更新订单状态补三阶段结构**

```text
1. 校验新状态、筛选条件和金额边界；
2. 只把已提供的筛选条件加入 UpdateWrapper；
3. 执行更新并根据影响行数返回。
```

### Task 2: MyBatis-Plus 六种 JOIN 的 Java 等价组装

**Files:**
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisPlusServiceImpl.java:395-543`

**Interfaces:**
- Consumes: 六个现有 JOIN 等价方法及 VO 转换辅助方法。
- Produces: 每种 JOIN 的数据读取、索引、过滤、组装和排序路线图。

- [x] **Step 1: INNER JOIN 五阶段注释**

```text
1. 校验订单号并查询订单；
2. 查询订单所属用户，任一主记录缺失时返回空结果；
3. 查询订单明细，明细为空时返回空结果；
4. 按商品ID集合批量查询商品并建立索引；
5. 过滤关联不完整的明细并组装 OrderDetailVO。
```

- [x] **Step 2: LEFT JOIN 三阶段注释**

```text
1. 查询完整用户列表作为左侧保留集合；
2. 查询身份证并按 userId 建立索引；
3. 遍历所有用户，缺失身份证时保留用户并返回空关联字段。
```

- [x] **Step 3: RIGHT JOIN 四阶段注释**

```text
1. 查询作为保留侧的完整用户列表；
2. 查询订单并按 userId 分组；
3. 建立订单时间和ID倒序比较器；
4. 为每个用户输出全部订单，没有订单时补一条空订单结果。
```

- [x] **Step 4: FULL OUTER JOIN 五阶段注释**

写明“读取两侧全集 → 商品索引与引用集合 → 保留全部订单明细并标记孤儿 → 补未售商品 → 按商品和明细ID排序”。

- [x] **Step 5: CROSS JOIN 四阶段注释**

写明“校验 limit → 查询商品和用户 → 双层循环生成候选 → 达到 limit 立即返回”。

- [x] **Step 6: LATERAL JOIN 三阶段注释**

写明“建立最新订单比较规则 → 每个 userId 合并保留最新订单 → 遍历全部用户并保留无订单用户”。

### Task 3: MyBatis-Plus 聚合、子查询和转换案例

**Files:**
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisPlusServiceImpl.java:548-737`

**Interfaces:**
- Consumes: 现有集合分组、过滤、排序和 VO 转换逻辑。
- Produces: 对应 GROUP BY、EXISTS、NOT EXISTS、HAVING、UNION ALL、CASE、COALESCE、窗口函数和 CTE 的分阶段说明。

- [x] **Step 1: 为用户订单统计和消费排行补步骤结构**

`getUserOrderStats` 写明“订单按用户分组 → 遍历完整用户集合聚合 → 按约定排序”；
`getTopSpendingUsers` 写明“校验 limit → 取得统一统计结果 → 排除零订单并截断”。

- [x] **Step 2: 为 EXISTS、NOT EXISTS 和 HAVING 补步骤结构**

`getUsersWithOrders` 写明“提取订单用户ID → 空集合提前返回 → IN 查询并转换”；
`getUsersWithoutOrders` 写明“提取订单用户ID → 遍历用户全集做差集 → 转换”；
`getUsersByOrderCount` 写明“校验下界 → 复用统计结果过滤 → 重新应用 HAVING 结果排序”。

- [x] **Step 3: 为 UNION ALL 补四阶段结构**

写明“查询 ACTIVE → 查询非 ACTIVE 与 NULL → 按 UNION ALL 拼接且不去重 → 排序并转换”。

- [x] **Step 4: 为两种状态名称案例补步骤结构**

`getOrderStatusWithName` 写明“查询订单 → String 状态调用手工映射 → 组装扁平 VO”；
`getOrderStatusWithEnumMapping` 写明“查询专用枚举投影 → 处理 NULL 或读取 code/text → 组装扁平 VO”。

- [x] **Step 5: 为 COALESCE 和日期转换案例补步骤结构**

`getUsersWithCoalescePhone` 写明“查询用户 → NULL 手机号替换为展示文本 → 转换 VO”；
`getUserDateStats` 写明“查询用户 → 保留原始时间 → 非空时间提取日期和年份”。

- [x] **Step 6: 为排名、消费分层和商品销售统计补步骤结构**

`getUserSpendingRank` 写明“取得非零订单统计 → 保持消费排序 → 按索引生成连续排名”；
`getUserSpendingLevels` 写明“取得用户统计 → 根据总金额划分等级 → 组装 VO”；
`getProductSalesStats` 写明“读取商品和明细 → 按商品分组明细 → 逐商品计算数量与销售额 → 排序返回”。

### Task 4: MyBatis-Plus 完整下单事务

**Files:**
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisPlusServiceImpl.java:739-836`

**Interfaces:**
- Consumes: 当前事务实现、`mergeQuantities`、`executeUniqueWrite`、原子库存 UpdateWrapper。
- Produces: 开头八步总览，与方法中八个阶段完全一致。

- [x] **Step 1: 在方法开头增加八步实现总览**

八步依次为“校验请求与用户 → 合并数量 → 批量查询并校验商品 → 计算可信金额 → 写订单头 → 构造并排序明细 → 写明细并原子扣库存 → 组装响应”。

- [x] **Step 2: 统一现有阶段编号格式**

把现有 `第 1 步` 统一为 `第1步`；将“第6、7步”拆成清晰的第6步和第7步代码阶段，同时保持循环和写入顺序不变。

- [x] **Step 3: 将并发与事务解释归入对应阶段**

商品ID排序说明放入第6步；原子条件 UPDATE、影响行数为零和事务回滚说明放入第7步；事务提交说明放入第8步。

### Task 5: 普通 MyBatis 多阶段编排方法

**Files:**
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisServiceImpl.java:272-564`

**Interfaces:**
- Consumes: 普通 MyBatis 分页、批量、枚举转换和完整下单实现。
- Produces: 仅为实际多阶段编排增加步骤清单，不重复 XML 内部 SQL 逻辑。

- [x] **Step 1: 为两种分页补四阶段结构**

写明“校验分页参数 → COUNT 总数 → 计算 offset 并查询当前页 → 封装 PageResult”。

- [x] **Step 2: 为用户批量写和条件状态更新补步骤结构**

批量新增写明“校验批次 → 校验每个用户 → 单条批量 SQL → 比较影响行数”；
批量状态更新和删除写明参数校验、Mapper 执行与影响行数判断；
订单条件更新写明安全校验、XML 动态条件更新和结果判断。

- [x] **Step 3: 为枚举映射结果转换补三阶段结构**

写明“XML 查询原始 code 并自动映射枚举 → 处理 NULL 或读取 code/text → 组装扁平 VO”。

- [x] **Step 4: 为完整下单增加八步总览并统一阶段编号**

八步与现有业务顺序保持一致：“校验 → 合并 → 批量加载 → 计算金额 → 订单头 → 明细与排序 → 批量写明细并原子扣库存 → 响应”。保留普通 MyBatis 批量插入与 Plus 逐条插入的差异说明。

### Task 6: 静态一致性验证

**Files:**
- Verify: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisPlusServiceImpl.java`
- Verify: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisServiceImpl.java`

**Interfaces:**
- Consumes: Task 1-5 的注释调整。
- Produces: 注释格式、编号和代码未误改的静态证据。

- [x] **Step 1: 检查所有实现步骤和阶段编号**

Run:

```bash
rg -n "实现步骤|// 第[0-9]+步" \
  src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisPlusServiceImpl.java \
  src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisServiceImpl.java
```

逐方法确认编号从1连续增长，且总览与代码阶段一一对应。

- [x] **Step 2: 检查业务结构未被误改**

核对两文件的方法签名、`@Transactional`、Mapper 调用、Wrapper 条件、提前返回和返回语句仍与修改前一致，只增加或移动注释。

- [x] **Step 3: 检查格式**

Run:

```bash
git diff --check
rg -n "[[:blank:]]+$" \
  src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisPlusServiceImpl.java \
  src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisServiceImpl.java
```

预期 `git diff --check` 退出码为0，尾随空格搜索无匹配。不运行测试或构建。
