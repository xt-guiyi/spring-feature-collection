# PostgreSQL Dual Data Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于五张 PostgreSQL 表实现行为一致的普通 MyBatis 与官方 MyBatis-Plus 两套学习入口，覆盖完整 CRUD、常用查询、六类 JOIN、复杂统计和事务下单。

**Architecture:** 两套 Controller 使用不同 URL 前缀，共享请求 DTO、响应 VO、实体和统一 Service 契约。普通 MyBatis 通过拆分后的 XML Mapper 执行数据库原生 SQL；MyBatis-Plus 通过五个官方 BaseMapper 完成单表操作，并在 Service 层组装复杂多表结果。

**Tech Stack:** Java 21、Spring Boot 4.1、MyBatis-Plus 3.5.17（Boot 4 Starter）、MyBatis XML、PostgreSQL、Lombok、Jakarta Validation。

## Global Constraints

- 不引入 `mybatis-plus-join` 或其他第三方 JOIN 扩展。
- 两套入口分别为 `/api/playground/pg/mybatis/**` 和 `/api/playground/pg/mybatis-plus/**`。
- 五张表必须全部提供两套完整 CRUD。
- 两套入口的请求、响应、排序和异常语义保持一致。
- 核心类、Service 编排和 XML SQL 使用详细中文学习型注释。
- 所有事务显式使用 `playgroundTransactionManager`。
- 不新增测试、不运行测试、不运行构建、不提交 Git；仅做静态一致性检查。
- 不修改与 PostgreSQL playground 无关的用户改动。

---

### Task 1: 建立共享接口契约、请求 DTO 和响应 VO

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/PgDataAccessService.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/dto/request/UserQueryRequest.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/dto/request/ProductQueryRequest.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/dto/request/CompleteOrderCreateRequest.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/dto/request/CompleteOrderItemRequest.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/vo/ProductOrderAuditVO.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/vo/UserProductCandidateVO.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/vo/UserSpendingLevelVO.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/vo/ProductSalesStatVO.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/vo/CompleteOrderResponse.java`
- Modify: existing request DTOs and VOs only where the shared contract needs an additional field.
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/dto/request/UserComplexQueryRequest.java`

**Interfaces:**
- Consumes: existing five entities, `OrderQueryRequest`, `OrderStatusUpdateRequest`, `UserStatusUpdateRequest`, existing VO classes and `PageResult`.
- Produces: one `PgDataAccessService` method contract implemented by both technologies.

- [ ] Define five-table CRUD methods with signatures such as `Long createUser(PgUser user)`, `PgUser getUser(Long id)`, `List<PgUser> listUsers()`, `boolean updateUser(PgUser user)`, and `boolean deleteUser(Long id)`.
- [ ] Define common single-table, batch, JOIN, aggregate, ranking, CTE-equivalent and complete-order transaction methods.
- [ ] Add Jakarta validation to complete-order requests: non-null user ID, nonblank order number, nonempty items, positive product ID and quantity.
- [ ] Add Chinese Javadoc describing which methods are paired learning cases and which return nullable outer-join fields.

### Task 2: 配置 XML 加载并建立五个 MyBatis-Plus BaseMapper

**Files:**
- Modify: `src/main/java/com/xt/xiaoxingxing/shared/config/PlaygroundMyBatisConfig.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgUserPlusMapper.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgIdCardPlusMapper.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgOrderPlusMapper.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgProductPlusMapper.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgOrderProductPlusMapper.java`
- Delete: old ambiguous `PgUserMapper.java`, `PgOrderMapper.java`, and `PgProductMapper.java`.

**Interfaces:**
- Consumes: five existing entity classes.
- Produces: five `BaseMapper<T>` beans and explicit XML resource loading.

- [ ] Configure `MybatisSqlSessionFactoryBean#setMapperLocations` with `classpath*:mapper/**/*.xml`.
- [ ] Create one documented empty `BaseMapper` interface per table.
- [ ] Preserve the existing PostgreSQL pagination interceptor and playground transaction manager.

### Task 3: 实现普通 MyBatis 五表 CRUD、条件、分页和批量 SQL

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgMyBatisUserMapper.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgMyBatisOrderMapper.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgMyBatisProductMapper.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgMyBatisRelationMapper.java`
- Create: `src/main/resources/mapper/postgresql/PgMyBatisUserMapper.xml`
- Create: `src/main/resources/mapper/postgresql/PgMyBatisOrderMapper.xml`
- Create: `src/main/resources/mapper/postgresql/PgMyBatisProductMapper.xml`
- Create: `src/main/resources/mapper/postgresql/PgMyBatisRelationMapper.xml`

**Interfaces:**
- Consumes: entities and shared query DTOs from Task 1.
- Produces: XML-backed CRUD methods, generated IDs, dynamic search, count/page, batches, conditional stock decrement and order-item batch insertion.

- [ ] Use PostgreSQL `INSERT ... RETURNING id` for each create method.
- [ ] Use `<set>` and `<if>` for non-null field updates.
- [ ] Implement `LIKE`, `IN`, product-price `BETWEEN`, `IS NULL`, sorting and dynamic order conditions.
- [ ] Implement `COUNT + LIMIT/OFFSET` pagination for users and orders.
- [ ] Implement `<foreach>` user batch insert/status update/delete and order-item batch insert.
- [ ] Implement conditional stock decrement with `WHERE stock >= quantity`.
- [ ] Add XML comments explaining every SQL concept and empty-collection preconditions.

### Task 4: 实现普通 MyBatis 六类 JOIN 与复杂 PostgreSQL SQL

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgMyBatisQueryMapper.java`
- Create: `src/main/resources/mapper/postgresql/PgMyBatisQueryMapper.xml`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/mapper/PgComplexMapper.java`
- Delete: `src/main/resources/mapper/PgComplexMapper.xml`

**Interfaces:**
- Consumes: existing and new VO classes.
- Produces: methods for INNER, LEFT, RIGHT, FULL OUTER, CROSS, LATERAL, aggregate, EXISTS, NOT EXISTS, HAVING, UNION ALL, CASE, COALESCE, date functions, ROW_NUMBER and CTE.

- [ ] Implement four-table inner order detail query.
- [ ] Implement left user/id-card, right order/user, full product/order-item audit, bounded cross user/product, and latest-order lateral queries.
- [ ] Implement user order statistics, top spenders, users with/without orders, minimum order count and product sales statistics.
- [ ] Implement union, status name, contact default, registration date, spending rank and spending-level CTE queries.
- [ ] Add detailed comments for driving tables, retained sides, null semantics, aggregation stages and PostgreSQL-specific behavior.

### Task 5: 实现普通 MyBatis Service

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/PgMyBatisService.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisServiceImpl.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/PgBusinessService.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgBusinessServiceImpl.java`

**Interfaces:**
- Consumes: Tasks 3-4 mappers and `PgDataAccessService`.
- Produces: `PgMyBatisService` bean with complete paired behavior.

- [ ] Implement CRUD validation and missing-record `BusinessException` handling.
- [ ] Build `PageResult` using count/page mapper calls with validated page bounds.
- [ ] Delegate each JOIN and complex query to one XML statement.
- [ ] Implement complete-order transaction: validate user/products, calculate amount, insert order/items, decrement inventory, and roll back on any affected-row mismatch.
- [ ] Add comments explaining database-side computation, statement count and transaction boundary.

### Task 6: 实现官方 MyBatis-Plus Service 与 Java 等价组装

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/PgMyBatisPlusService.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/service/impl/PgMyBatisPlusServiceImpl.java`

**Interfaces:**
- Consumes: Task 2 BaseMappers and `PgDataAccessService`.
- Produces: `PgMyBatisPlusService` bean with outputs matching Task 5.

- [ ] Implement five-table CRUD with official BaseMapper methods.
- [ ] Implement dynamic filtering and pagination with `LambdaQueryWrapper`, `LambdaUpdateWrapper` and `Page`.
- [ ] Implement batches in explicit playground transactions.
- [ ] Reproduce six JOIN semantics with maps, grouping and null-preserving Java assembly; batch-load data to avoid N+1 queries.
- [ ] Reproduce aggregation, EXISTS, HAVING, UNION, CASE, COALESCE, date, ranking and CTE outputs with Java collections.
- [ ] Use conditional `setDecrBy` inventory updates and affected-row checks in complete-order transaction.
- [ ] Add comments stating SQL count, memory cost and differences from database-native SQL.

### Task 7: 建立共享端点定义和两套清晰 Controller 入口

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/controller/AbstractPgDataAccessController.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/controller/PgMyBatisController.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/controller/PgMyBatisPlusController.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/postgresql/controller/PgBusinessController.java`

**Interfaces:**
- Consumes: shared service contract and the two concrete service interfaces.
- Produces: identical endpoint suffixes below two distinct class-level URL prefixes.

- [ ] Put shared endpoint mappings in the abstract controller so method signatures cannot drift.
- [ ] Add complete CRUD routes for all five resources.
- [ ] Add paired search, page, batch, conditional update, six JOIN, complex query and complete-order routes.
- [ ] Add `@Valid` to validated request bodies and Chinese Javadoc explaining the paired implementations.
- [ ] Bind each concrete controller constructor to the correct concrete service.

### Task 8: 清理旧实现并执行静态验收

**Files:**
- Delete: obsolete mixed Controller, Service, Mapper, XML and unused DTO files listed above.
- Modify: imports or references affected by renamed mappers and services.

**Interfaces:**
- Consumes: all previous tasks.
- Produces: one coherent PostgreSQL playground module without mixed legacy entry points.

- [ ] Search for `PgBusiness`, `PgComplexMapper`, old mapper names and `UserComplexQueryRequest`; remove every obsolete reference.
- [ ] Compare abstract controller routes against `PgDataAccessService` methods and both implementations.
- [ ] Compare each MyBatis Mapper method against XML namespace, statement ID, parameter names and result types.
- [ ] Confirm six JOIN keywords exist in MyBatis XML and matching Plus assembly methods exist.
- [ ] Confirm all transaction annotations name `playgroundTransactionManager`.
- [ ] Review `git status --short` and ensure unrelated user changes were not modified.
- [ ] Do not invoke Maven, Java compilation, tests or application startup.
