# RocketMQ Transaction-Only Flattening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除 Outbox，只保留并扁平化 RocketMQ 事务消息订单链，使 RocketMQ 模块拥有统一 DTO、实体、Mapper、消息协议和业务服务。

**Architecture:** 事务消息链直接落在 `rocketmq` 的统一技术分层中；订单持久化不再委托 PostgreSQL 学习模块。Topic、Tag、Group 和默认值进入 Java，YAML 只保留连接与实际环境覆盖。

**Tech Stack:** Java 21、Spring Boot 4.1、Spring Transaction、MyBatis/MyBatis-Plus、RocketMQ v5 Client Starter 2.3.6、PostgreSQL、Redis、Jackson 3。

**Spec:** `docs/superpowers/specs/2026-08-23-rocketmq-transaction-only-flattening-design.md`

## Global Constraints

- 不运行测试或构建；只做静态引用、目录与差异核对。
- 保留用户未提交的 `application-dev.yaml` 中 `order-timeout-millis: 100000`。
- 不恢复 `RocketMqLearningProperties` 中用户已删除的配置交叉校验。
- 移动 `MqTransactionRecordMapper.xml` 时保留当前工作树内容，不回退用户改动。
- 不新增 `interface/impl`、Facade、Factory 或一行转发包装类。
- 不修改 RocketMQ 事务消息的核心可靠性时序。

---

### Task 1: 删除 Outbox 完整链路

**Files:**
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/outbox/**`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/infrastructure/OutboxRelay.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/entity/MqOutboxEvent.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/mapper/MqOutboxEventMapper.java`
- Delete: `src/main/resources/mapper/rocketmq/MqOutboxEventMapper.xml`
- Modify: `docs/schema-demo.sql`

- [x] 删除九个 Outbox Java 文件和对应 Mapper XML。
- [x] 删除 `mq_outbox_event` 的建表、注释和索引；全量重建脚本只保留兼容旧库的清理 DROP。
- [x] 保留消费幂等、统计和事务记录表。

### Task 2: 建立 RocketMQ 自有订单 DTO、实体和 Mapper

**Files:**
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/dto/CreateOrderRequest.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/dto/CreateOrderItemRequest.java`
- Move/Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/dto/OrderResponse.java`
- Move/Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/dto/ProductResponse.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/entity/Order.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/entity/OrderItem.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/entity/Product.java`
- Replace: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/mapper/MqOrderBusinessMapper.java` with `OrderMapper.java`
- Replace: `src/main/resources/mapper/rocketmq/MqOrderBusinessMapper.xml` with `OrderMapper.xml`

**Produces:** `OrderMapper` 提供用户存在检查、商品批量查询、订单/明细插入、库存条件扣减、订单查询、支付、取消和库存恢复。

- [x] 新建 HTTP 请求 DTO 和 RocketMQ 自有订单实体。
- [x] 将响应对象移动到统一 `dto` 包并改为依赖新实体。
- [x] 合并完整下单 SQL 与现有订单生命周期 SQL 到 `OrderMapper`。
- [x] 让后续 Service 不再 import `playground.postgresql.*`。

### Task 3: 扁平迁移消息、仓储和任务组件

**Files:**
- Rename/Modify: `config/RocketMqNames.java` to `config/RocketMqConstants.java`
- Move/Modify: `message/OrderTransactionCommands.java` into four top-level message DTO files
- Delete: dedicated message decoding exception; reuse shared `BusinessException`
- Move/Modify: `support/RocketMessageCodec.java` to `util/RocketMessageCodec.java`
- Move/Modify: publisher helper to `util/RocketMqUtil.java`
- Move: `support/RocketConsumerSupport.java` to `listener/RocketConsumerSupport.java`
- Move: `infrastructure/TransactionRecordRepository.java` to `repository/TransactionRecordRepository.java`
- Move: `infrastructure/OrderTransactionChecker.java` to `checker/OrderTransactionChecker.java`
- Move: `infrastructure/PreparedTransactionCleanupTask.java` to `task/PreparedTransactionCleanupTask.java`

- [x] 删除 `Command` 聚合容器并建立 `CreateOrderMessage`、`OrderItemMessage`、`PayOrderMessage`、`CancelOrderMessage`。
- [x] 删除 Codec 和 Publisher 中仅供 Outbox 使用的方法。
- [x] 修改所有 package/import，但不改变事务状态机。

### Task 4: 扁平迁移 Controller、Service 和 Listener

**Files:**
- Move/Modify: `order/transaction/OrderController.java` to `controller/OrderController.java`
- Move/Modify: `order/transaction/OrderService.java` to `service/OrderService.java`
- Move/Modify: `order/OrderEventHandler.java` to `service/OrderEventService.java`
- Move: `product/ProductController.java` to `controller/ProductController.java`
- Move/Modify: `product/ProductService.java` to `service/ProductService.java`
- Move/Modify: `order/transaction/listener/*.java` to `listener/*.java`

- [x] 将订单接口改为 `/api/playground/rocketmq/orders`，商品接口保持不变。
- [x] 将 `OrderService` 的完整下单逻辑改为直接调用 `OrderMapper`。
- [x] 删除 Outbox/双方案措辞和重复内部校验。
- [x] 更新 Listener 的包、注入类型和常量引用。

### Task 5: 收敛配置

**Files:**
- Modify: `config/RocketMqLearningProperties.java`
- Modify/Delete: `config/RocketMqListenerAnnotationEnhancer.java`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/resources/application-dev.yaml`

- [x] 从属性类删除 Outbox、普通 Topic、Outbox Tag 和三组 Outbox ConsumerGroup。
- [x] 为业务时间、清理和缓存参数提供 Java 默认值。
- [x] 将 Topic、Group 和注解必需的事件字符串收口到 `RocketMqConstants`，由 `OrderOperation` 统一操作/事件映射。
- [x] 删除 YAML 中 SDK 默认值和 Outbox 配置，保留连接信息及开发环境 100000ms 超时覆盖。
- [x] 精简注解增强器，只同步 YAML 中的 SSL 连接开关；超时、线程和缓存使用 SDK 默认值。

### Task 6: 更新当前文档并静态核对

**Files:**
- Rewrite: `docs/rocketmq-reliable-messaging-learning.md`
- Modify: `docs/xxl-job-learning.md` only where it claims this project contains Outbox

- [x] 将学习文档重写为单一事务消息链，删除 Outbox 接口、章节、SQL 和资源表。
- [x] 搜索 `Outbox|outbox|order.transaction|order.outbox|playground.postgresql`，修复运行代码中的遗留引用。
- [x] 检查最终 Java 目录只保留批准的统一技术分层。
- [x] 使用 `git diff --check` 和 `git diff --stat` 核对空白错误与变更范围；不运行测试或构建。
