# RocketMQ Business Order Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 RocketMQ 学习模块重构成贴近真实业务的两套订单实现：Outbox 版与 RocketMQ 事务消息版都只暴露 `createOrder`、`payOrder`、`cancelExpiredOrder` 等业务语义，同时删除技术演示命名、过度拆分的 Service 和重复字段。

**Architecture:** 通过 `order.outbox` 与 `order.transaction` 包区分可靠消息机制，每个包只保留一个订单 `OrderService`。Listener、Outbox 中继、事务回查与 PREPARED 清理保留独立 Spring 边界，但统一改成 Listener/Relay/Checker/Task/Repository 等真实职责命名。两套订单实现共享 `OrderEventHandler`、商品缓存服务、Mapper、消息信封和业务响应，不通过 `mode` 参数混合流程。

**Tech Stack:** Java 21、Spring Boot 4.1、Spring TransactionTemplate、MyBatis/MyBatis-Plus、PostgreSQL、Redis、Apache RocketMQ 5.5、RocketMQ v5 Spring Boot Starter 2.3.6、Jackson 3。

## Global Constraints

- [x] 严格保留当前脏工作区以及所有无关修改；只编辑本计划列出的 RocketMQ、SQL 和学习文档文件。
- [x] 按用户要求不编写测试、不运行测试、Maven、应用、容器或数据库脚本；验收只做引用、XML、YAML、SQL 和 diff 静态检查。
- [x] 不创建兼容代理类、废弃转发方法或兼容 ALTER SQL；旧类和旧字段迁移完成后直接删除。
- [x] 所有复杂业务方法先写“完整步骤”，再按“第 1 步、第 2 步……”添加充分中文学习注释，重点解释事务边界、重复投递、竞态和失败恢复。
- [x] 不提交 Git；每个任务完成后仅用 `git diff --check` 和范围扫描核对。

---

## Task 1: 固化公共业务契约与精简事务记录模型

**Files:**

- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/OrderResponse.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/message/OrderTransactionCommands.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/entity/MqTransactionRecord.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/mapper/MqTransactionRecordMapper.java`
- Modify: `src/main/resources/mapper/rocketmq/MqTransactionRecordMapper.xml`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/config/RocketMqNames.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/util/RocketMqUtil.java`
- Delete later after caller migration: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/message/TransactionOrderCommandPayload.java`
- Delete later after caller migration: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/message/TransactionOrderItemPayload.java`
- Delete later after caller migration: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/vo/OutboxOrderCreateVO.java`
- Delete later after caller migration: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/vo/OutboxOrderPayVO.java`
- Delete later after caller migration: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/vo/RocketTransactionOrderVO.java`

- [x] 创建统一 `OrderResponse`，只包含 `orderId/orderNo/status/totalAmount/itemCount`，提供从已落库订单及明细构造的工厂方法，不暴露机制名、事务状态、Broker messageId 或解释性文案。
- [x] 用一个 `OrderTransactionCommands` 容器定义 `CreateOrderCommand(orderNo,userId,items)` 和 `OrderIdCommand(orderId)`；CREATE 与 PAY/CANCEL 不再共用含大量空字段的通用 DTO。
- [x] 将 `MqTransactionRecord` 精简为 `transactionId/businessType/businessKey/operationType/status/lastError/createdAt/updatedAt`；事务基础设施不保存 `orderNo/orderId` 等订单领域专用字段。
- [x] 重写事务记录 Mapper/XML：按 `transaction_id` 查询和更新；活跃记录按 `(business_type, business_key, operation_type)` 防重；保留 PREPARED 条件终态竞争、过期候选查询和通用已提交操作判断。
- [x] 删除 `RocketMqNames.HEADER_TRANSACTION_ID`；事务记录主键 `transactionId` 同时就是信封 `messageId`，订单事务消息的 `aggregateId/businessKey` 统一使用稳定订单号。
- [x] `RocketMqUtil.sendTransaction` 只接收 `topic/tag/key/payload`，不再复制 transactionId 到自定义 Header。
- [x] 静态核对：Mapper Java 方法与 XML statement 一一对应，旧 `message_id/order_no/order_id/HEADER_TRANSACTION_ID/TransactionOrderCommandPayload` 不再出现在事务记录运行链路。

## Task 2: 建立事务记录基础设施、Broker 回查与 PREPARED 清理

**Files:**

- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/infrastructure/TransactionRecordRepository.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/infrastructure/OrderTransactionChecker.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/infrastructure/PreparedTransactionCleanupTask.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/RocketTransactionRecordService.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/transaction/RocketOrderTransactionChecker.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/schedule/PreparedTransactionCleanupScheduler.java`

- [x] 将原记录 Service 收敛为基础设施 `TransactionRecordRepository`；公开方法只表达通用 `prepare/findById/isCommitted/markCommitted/markRolledBack/findExpiredPrepared` 等持久化语义。
- [x] `prepare` 接收 `businessType + businessKey + operationType`；订单业务传 `ORDER + orderNo + CREATE/PAY/CANCEL`，重复活跃操作由数据库部分唯一索引最终裁决，异常转换为可理解的业务冲突。
- [x] 所有终态更新继续使用 `PREPARED -> COMMITTED/ROLLED_BACK` 条件 UPDATE：`ROLLED_BACK` 使用 `REQUIRES_NEW` 独立收口；`COMMITTED` 必须以 `MANDATORY` 加入订单事实所在的本地事务，确保订单/库存与事务记录一起提交或回滚；更新 0 行时重读当前状态而不是覆盖并发赢家。
- [x] 将 Broker 回查类改名为 `OrderTransactionChecker`：解码并校验信封后直接使用 `envelope.messageId` 查事务记录；根据持久化终态返回 COMMIT/ROLLBACK/UNKNOWN。
- [x] 对超过保护窗口的 PREPARED 记录，Checker 必须先用条件更新抢占 `ROLLED_BACK` 终态，成功才返回 ROLLBACK；更新失败后重读，避免本地事务晚提交导致消息丢失。
- [x] 将清理调度改名为 `PreparedTransactionCleanupTask`，它只清理“PREPARED 已提交但半消息根本没到 Broker”的孤儿窗口，并与本地事务/Checker 通过同一条件终态安全竞争。
- [x] 为三个基础设施类补全中文步骤和竞态注释；显式 Bean 名保持唯一且配置开关不变。
- [x] 静态核对：旧 Service/transaction/schedule 类名无运行引用；回查链路不存在自定义 transaction header 或 `record.messageId`。

## Task 3: 将事务消息版完整订单生命周期收敛为一个业务 OrderService

**Files:**

- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/transaction/OrderService.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/transaction/OrderController.java`
- Move/Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/transaction/OrderStateConflictException.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/controller/RocketTransactionOrderController.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/RocketTransactionMessageService.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/RocketTransactionLocalService.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/RocketTransactionTimeoutSchedulerService.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/RocketTransactionTimeoutExecutorService.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/TransactionOrderStateConflictException.java`

- [x] 创建显式 Bean 名 `transactionOrderService` 的事务版 `OrderService`，公开方法只保留 `createOrder`、`payOrder`、`schedulePaymentTimeout`、`cancelExpiredOrder`。
- [x] `createOrder` 生成一个 UUID 作为 `transactionId=envelope.messageId`，信封 `aggregateId=orderNo`；执行“PREPARED -> 半消息 -> PostgreSQL 本地事务创建订单/扣库存/COMMITTED -> commit 半消息”。
- [x] `payOrder` 校验订单来自事务消息 CREATE 链，先按本地 `orderId` 读取稳定 `orderNo`，再生成新的事务 ID；信封 `aggregateId/businessKey=orderNo`，本地短事务用 `WHERE status='PENDING'` 更新支付并提交 `ORDER_PAID`。
- [x] `cancelExpiredOrder` 与支付使用同一 `PENDING` 条件更新竞争；只有取消成功的一方恢复库存并提交 `ORDER_CANCELLED`，过期重复消息不重复恢复库存。
- [x] `schedulePaymentTimeout` 在已提交 CREATE 消费后，根据订单创建时间计算剩余延迟；从 CREATE `messageId` 稳定派生超时消息 ID，重复投递不会创建新的业务幂等键。
- [x] 使用 `TransactionTemplate` 只包裹 PostgreSQL 本地事务段；发送半消息、commit/rollback RPC 位于数据库事务外，避免长期占用连接与锁。
- [x] 捕获本地事务异常时先读取持久记录裁决：COMMITTED 则 commit 半消息并返回真实订单，ROLLED_BACK 才 rollback，仍 PREPARED 或查询失败时不猜测错误终态。
- [x] 本地数据库已经 COMMITTED、但 Broker commit RPC 结果不明确时，HTTP 仍返回 `OrderResponse`；日志说明由 Broker 回查收敛，不向前端暴露 UNKNOWN。
- [x] 新 `OrderController` 保持原路径 `/api/playground/rocketmq/transaction/orders`，方法名为 `create/pay`，响应统一 `Result<OrderResponse>`。
- [x] 静态核对：事务流程不再注入任何旧技术 Service，公开业务方法不接收 Tag、信封、MessageView 或 `timeoutMessageId`。

## Task 4: 将事务版四个 Listener 移入业务包并只传业务参数

**Files:**

- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/transaction/listener/ProductCacheInvalidationListener.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/transaction/listener/OrderStatisticsListener.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/transaction/listener/PaymentTimeoutScheduleListener.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/transaction/listener/PaymentTimeoutListener.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/consumer/order/TransactionOrderCacheConsumer.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/consumer/order/TransactionOrderStatisticsConsumer.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/consumer/order/TransactionTimeoutSchedulerConsumer.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/consumer/order/TransactionOrderTimeoutConsumer.java`

- [x] 每个 Listener 使用显式唯一 Bean 名，继续保持原 Topic、Tag、ConsumerGroup 配置和 `@ConditionalOnProperty`。
- [x] 缓存/统计 Listener 解码后验证 Tag 与 eventType，再以 `envelope.messageId` 读取已提交事务事实，组装真实 `OrderEventPayload`，调用共享 `OrderEventHandler`。
- [x] 超时调度 Listener 只接受已提交 `ORDER_CREATED`，传 `orderNo + createdMessageId` 给事务 `OrderService.schedulePaymentTimeout`。
- [x] 超时执行 Listener 只接受 `TRANSACTION_PAYMENT_TIMEOUT_CHECK`，从 payload 取得 `orderNo + orderId` 后调用 `OrderService.cancelExpiredOrder(orderNo, orderId)`，并在业务服务中校验二者属于同一订单。
- [x] Listener 不执行订单 SQL、不持有业务事务、不返回自定义技术结果；业务异常交由 `RocketConsumerSupport` 转成 FAILURE 触发 Broker 重试。
- [x] 静态核对 4 个 Listener 的 Topic/Tag/Group 占位符与两份 YAML 完全一致，且不存在旧 Consumer 类名。

## Task 5: 将 Outbox 完整订单生命周期收敛为一个业务 OrderService 与 OutboxRelay

**Files:**

- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/outbox/OrderService.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/outbox/OrderController.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/infrastructure/OutboxRelay.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/controller/OutboxOrderController.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/OutboxOrderService.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/OutboxEventService.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/schedule/OutboxPublishScheduler.java`

- [x] 创建显式 Bean 名 `outboxOrderService` 的 Outbox `OrderService`，公开方法只保留 `createOrder`、`payOrder`、`cancelExpiredOrder`。
- [x] `createOrder` 在同一 PostgreSQL 事务中完成订单、明细、库存扣减、`ORDER_CREATED` Outbox 和付款超时 Outbox；插入事件的私有方法直接使用 Codec 与 Mapper，不再另建转发 Service。
- [x] `payOrder` 在同一事务中完成 `PENDING -> PAID` 条件更新与 `ORDER_PAID` Outbox；重复支付不重复写事件。
- [x] `cancelExpiredOrder` 在同一事务中完成 `PENDING -> CANCELLED`、按 productId 排序恢复库存和 `ORDER_CANCELLED` Outbox；支付抢先成功时只安全返回。
- [x] 新 `OrderController` 保持原路径 `/api/playground/rocketmq/outbox/orders`，两个公开接口统一返回 `Result<OrderResponse>`。
- [x] 将调度器改名为 `OutboxRelay`：用 `TransactionTemplate` 完成短事务领取，事务外同步发布，另一个短事务按 `id + lockedAt` 租约令牌回写 PUBLISHED/FAILED/DEAD。
- [x] `OutboxRelay` 直接依赖 Mapper、Codec、Publisher 和配置；保留指数退避、锁过期重领、最多重试与日志，但不包含订单业务 SQL。
- [x] 静态核对：Outbox 订单事务方法不进行 RocketMQ 网络调用；Relay 网络调用时不持有 PostgreSQL 事务。

## Task 6: 移动 Outbox Listener，并收敛共享订单副作用与商品缓存业务

**Files:**

- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/outbox/listener/ProductCacheInvalidationListener.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/outbox/listener/OrderStatisticsListener.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/outbox/listener/PaymentTimeoutListener.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/order/OrderEventHandler.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/product/ProductService.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/product/ProductController.java`
- Move/Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/product/ProductResponse.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/consumer/order/OutboxOrderCacheConsumer.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/consumer/order/OutboxOrderStatisticsConsumer.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/consumer/order/OutboxOrderTimeoutConsumer.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/RocketOrderConsumerService.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/RocketProductCacheService.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/controller/RocketProductCacheController.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/vo/RocketProductCacheVO.java`

- [x] `OrderEventHandler` 提供业务化方法 `invalidateProductCache` 与 `recordStatistics`；参数为 `consumerName/messageId/eventType/OrderEventPayload`，不接收 MessageView、Tag 或原始信封。
- [x] 消费幂等仍以 `(consumer_name,message_id)` 为唯一键；统计写入与消费记录在同一 PostgreSQL 事务中，重复消息直接返回但不重复累计。
- [x] 商品缓存失效继续真实调用 Redis 删除；Redis 异常必须抛出，让消息重试，不能先记幂等成功再吞异常。
- [x] `ProductService` 负责 Cache Aside 查询、缓存键和 TTL；`ProductController` 保持 `/api/playground/rocketmq/products/{productId}`，返回业务化 `ProductResponse`。
- [x] Outbox 三个 Listener 移入 `order.outbox.listener`，只做解码、路由校验和调用业务方法；超时 Listener 调用 Outbox `OrderService.cancelExpiredOrder(orderId)`。
- [x] 缓存组只接受 CREATED/CANCELLED，统计组接受 CREATED/PAID/CANCELLED；两套机制用不同 ConsumerGroup 作为幂等维度但调用相同业务副作用。
- [x] 静态核对：旧 `Rocket*Service/Controller/VO` 名称无运行引用；共享 Handler 中没有根据 Outbox/transaction 模式写大段 if/else。

## Task 7: 同步最终 SQL、删除冗余文件并重写学习导航

**Files:**

- Modify: `docs/schema-demo.sql`
- Modify: `docs/rocketmq-reliable-messaging-learning.md`
- Review only: `src/main/resources/application.yaml`
- Review only: `src/main/resources/application-dev.yaml`
- Delete: 本计划前述全部旧类、旧 VO 与旧消息 DTO

- [x] 将 `mq_transaction_record` DDL 改为最终 8 个通用字段；删除订单专用的 `order_no/order_id`，保留 `business_type/business_key/operation_type`，并添加活跃业务三元组部分唯一索引与通用非空白 CHECK。
- [x] 确保三张 MQ 基础设施表与 `order_statistics` 订单统计投影均无外键、无兼容 ALTER/DO/IF EXISTS 建表逻辑；每张表和每个字段都有准确中文注释。
- [x] 核对 `mq_outbox_event`、`mq_consumed_message`、`order_statistics` 不重新引入无用途字段；实体/XML/DDL 的列名、类型和可空性一致。
- [x] 重写学习文档代码导航、请求响应和 SQL 查询：两套入口都只叫创建/支付订单；技术机制由包和章节解释，不出现在业务方法名或响应字段中。
- [x] 文档逐步骤对照 Outbox 与事务消息，解释 Broker 故障、重复投递、支付/取消竞争、回查和孤儿 PREPARED 清理；明确生产系统同一业务只选择一种方案。
- [x] 核对两份 YAML 的三 Topic、五 Tag、七 ConsumerGroup 与 7 个 Listener 一致；配置值保持固定写法，不引入 `${ENV:default}`。
- [x] 删除所有旧 Service、Controller、Listener、VO、命令 DTO 和空目录，不删除任何无关模块文件。

## Task 8: 全量静态验收

**Files:**

- Verify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/**`
- Verify: `src/main/resources/mapper/rocketmq/**`
- Verify: `src/main/resources/application.yaml`
- Verify: `src/main/resources/application-dev.yaml`
- Verify: `docs/schema-demo.sql`
- Verify: `docs/rocketmq-reliable-messaging-learning.md`

- [x] 用 `rg` 确认旧类名、旧方法名、旧字段名、自定义 transaction header 和已删除 DTO 均为 0 个运行引用。
- [x] 用 `rg` 统计最终核心结构：2 个业务 `OrderService`、2 个业务 `OrderController`、7 个 Listener、4 个必要 support 文件、3 张 MQ 基础设施表和 1 张订单统计投影表。
- [x] 用 XML 解析工具静态检查 4 份 Mapper XML well-formed；逐项核对 Mapper statement ID 与 Java 方法名。
- [x] 用 YAML 解析工具静态检查两份配置；逐项核对 Topic/Tag/ConsumerGroup 的键和值一致。
- [x] 用脚本或文本扫描核对三张 MQ 基础设施表和 `order_statistics` 的每个字段都有 `COMMENT ON COLUMN`，且不存在 `REFERENCES/FOREIGN KEY/ALTER TABLE` 兼容迁移。
- [x] 执行 `git diff --check`；只报告静态通过项和用户仍需自行执行的 schema/启动验证，不声称运行成功。

## Task 9: 将订单统计投影表改为业务命名

**Files:**

- Modify: `docs/schema-demo.sql`
- Modify: `src/main/resources/mapper/rocketmq/MqConsumerRecordMapper.xml`
- Modify: `docs/rocketmq-reliable-messaging-learning.md`
- Modify: `docs/superpowers/specs/2026-08-14-rocketmq-business-order-refactor-design.md`

- [x] 将订单统计投影表统一命名为 `order_statistics`，因为它属于订单领域统计投影，不属于 MQ 基础设施。
- [x] 同步 `DROP TABLE`、`CREATE TABLE`、单例 CHECK 约束名、全部 `COMMENT ON` 以及 UPSERT 中的表名和自引用。
- [x] 同步学习文档、设计文档和本计划中的表名，明确它由消息驱动，但业务归属仍是订单统计。
- [x] 使用 `rg` 确认当前源码、Mapper、SQL和当前文档中旧技术表名为 0；解析 Mapper XML并执行 `git diff --check`。
- [x] 不编写或运行测试，不运行 Maven、应用和数据库脚本；由用户自行重建学习数据库验证。
