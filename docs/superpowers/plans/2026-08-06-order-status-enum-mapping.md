# 订单状态枚举自动映射 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增 MyBatis 与 MyBatis-Plus 成对接口，演示数据库订单状态码自动映射为 Java 枚举，同时保持响应为扁平的 `status`、`statusName`。

**Architecture:** 保留现有 `PgOrder.status` 字符串字段及 `CASE WHEN` 学习接口，新增带枚举字段的专用订单投影模型。普通 MyBatis 通过 XML 映射该投影，MyBatis-Plus 通过专用 `BaseMapper` 映射同一投影，Service 再将枚举的 code/text 转成共享 `OrderStatusVO`。

**Tech Stack:** Java 21、Spring Boot、MyBatis、MyBatis-Plus 3.5.17、PostgreSQL、Lombok。

## Global Constraints

- 两套接口仅 URL 前缀不同，返回结构必须一致。
- 返回 JSON 保持 `status`、`statusName` 平铺结构，不返回嵌套枚举对象。
- 新增核心类、Mapper、XML、Service 和 Controller 代码必须包含面向学习的中文注释。
- 不修改数据库结构，不改造现有 `PgOrder.status`，不删除原有状态名称对照接口。
- 按用户要求不新增或运行测试，不运行构建，只进行静态检查。
- 不提交 Git commit，保留工作区内其他未提交改动。

---

### Task 1: 枚举及专用查询投影

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/enums/OrderStatusEnum.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/entity/PgOrderStatusEnumDemo.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgOrderStatusEnumPlusMapper.java`

**Interfaces:**
- Produces: `OrderStatusEnum` 的 `getCode()`、`getText()`；`BaseMapper<PgOrderStatusEnumDemo>` 查询入口。

- [x] 新增包含 `PENDING`、`PAID`、`CANCELLED` 的枚举，并在 `code` 字段标记 `@EnumValue`。
- [x] 新增映射 `orders` 表的只读学习投影，字段为 `id`、`orderNo`、`status`。
- [x] 新增专用 MyBatis-Plus `BaseMapper`，注释说明它只用于演示枚举自动映射。

### Task 2: 成对查询与扁平响应

**Files:**
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgMyBatisQueryMapper.java`
- Modify: `src/main/resources/mapper/postgresql/PgMyBatisQueryMapper.xml`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/PgDataAccessService.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisServiceImpl.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisPlusServiceImpl.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/controller/AbstractPgDataAccessController.java`

**Interfaces:**
- Produces: `List<OrderStatusVO> getOrderStatusWithEnumMapping()`。

- [x] 普通 MyBatis XML 查询 `id`、`order_no`、`status`，让 MyBatis 类型处理器把状态码写入枚举字段。
- [x] MyBatis-Plus 使用专用 BaseMapper 和 Lambda Wrapper 查询相同字段并按订单 ID 升序。
- [x] 两套 Service 都从枚举读取 code/text，设置 `OrderStatusVO.status` 和 `statusName`，不调用 `statusName(String)`。
- [x] 在抽象 Controller 新增 `GET /queries/order-status-enums`，由两套 Controller 前缀自动形成成对入口。

### Task 3: 静态验证

**Files:**
- Verify: 上述全部新增和修改文件。

- [x] 检查两套 Service 均实现新增契约，Controller 路径唯一且返回 `OrderStatusVO`。
- [x] 检查枚举 code 存在唯一 `@EnumValue`，Plus Mapper 继承正确的 BaseMapper 泛型。
- [x] 检查 XML statement ID 与 Mapper 方法名一致，新增文件无尾随空格。
- [x] 仅运行 `rg`、`xmllint` 和 `git diff --check` 静态检查，不运行测试或构建。
