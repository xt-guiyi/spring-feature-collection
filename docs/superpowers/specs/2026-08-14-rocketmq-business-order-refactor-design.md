# RocketMQ 订单案例业务化重构设计

## 1. 目标

当前 RocketMQ 学习模块已经能够完整演示 Outbox 和 RocketMQ 事务消息，但业务代码被可靠性机制名称主导：

- `RocketTransactionMessageService`、`RocketTransactionLocalService` 等类名描述技术步骤，不描述订单职责；
- `createTransactionOrder`、`payTransactionOrder` 把消息机制暴露进业务方法；
- 一个订单生命周期被拆成过多 `Service`，阅读者必须跨多个文件才能理解一次创建订单；
- HTTP 响应和事务消息命令中存在只为展示技术细节而保存的重复字段。

本次重构的目标是：业务层只表达“创建订单、支付订单、超时取消订单”，Outbox、半消息、回查和幂等只出现在包名及基础设施层。

两套方案仍同时保留，用于对照学习；真实生产业务只应选择其中一套。

## 2. 设计原则

1. **类名优先表达业务职责**：核心类叫 `OrderController`、`OrderService`、`ProductService`。
2. **包名表达实现方案**：通过 `order.outbox` 与 `order.transaction` 区分两套实现，不在业务方法名重复技术前缀。
3. **每套方案只有一个订单 Service**：创建、支付、超时取消收敛到同一个订单生命周期类。
4. **基础设施不伪装成 Service**：轮询发布叫 `OutboxRelay`，回查叫 `OrderTransactionChecker`，清理任务叫 `PreparedTransactionCleanupTask`，事务记录持久化叫 `TransactionRecordRepository`。
5. **不为了减少文件破坏真实边界**：不同 ConsumerGroup 的 Listener、Broker Checker 和后台调度任务仍必须是独立 Spring Bean。
6. **字段必须有独立用途**：同一含义只保存一次；仅用于技术演示、可由其他字段推导且不参与业务裁决的字段删除。
7. **不持有数据库事务执行 RocketMQ 网络调用**：事务消息协调方法不整体添加 `@Transactional`，只对真正的 PostgreSQL 写入段开启短事务。

## 3. 最终包结构

```text
com.xt.xiaoxingxing.playground.rocketmq
├── order
│   ├── outbox
│   │   ├── OrderController
│   │   ├── OrderService
│   │   └── listener
│   │       ├── ProductCacheInvalidationListener
│   │       ├── OrderStatisticsListener
│   │       └── PaymentTimeoutListener
│   ├── transaction
│   │   ├── OrderController
│   │   ├── OrderService
│   │   └── listener
│   │       ├── ProductCacheInvalidationListener
│   │       ├── OrderStatisticsListener
│   │       ├── PaymentTimeoutScheduleListener
│   │       └── PaymentTimeoutListener
│   └── OrderEventHandler
├── product
│   ├── ProductController
│   └── ProductService
├── infrastructure
│   ├── OutboxRelay
│   ├── TransactionRecordRepository
│   ├── OrderTransactionChecker
│   └── PreparedTransactionCleanupTask
├── mapper
├── entity
├── message
├── support
└── config
```

两个包中的 `OrderService`、`OrderController` 和部分 Listener 使用相同的业务类名。为避免 Spring 默认 Bean 名冲突，显式设置唯一 Bean 名，例如 `outboxOrderService`、`transactionOrderService`、`outboxProductCacheInvalidationListener`；Java 调用仍按各自完整包类型注入，不使用 `mode` 参数和大段 `if/else`。

## 4. 核心业务 API

### 4.1 Outbox 订单服务

```java
OrderResponse createOrder(CompleteOrderCreateRequest request);
OrderResponse payOrder(Long orderId);
void cancelExpiredOrder(Long orderId);
```

- `createOrder`：订单、明细、库存扣减、`ORDER_CREATED` Outbox、超时检查 Outbox 在一个 PostgreSQL 事务提交。
- `payOrder`：`PENDING -> PAID` 条件更新和 `ORDER_PAID` Outbox 在一个事务提交。
- `cancelExpiredOrder`：`PENDING -> CANCELLED`、库存恢复、`ORDER_CANCELLED` Outbox 在一个事务提交。

### 4.2 事务消息订单服务

```java
OrderResponse createOrder(CompleteOrderCreateRequest request);
OrderResponse payOrder(Long orderId);
void schedulePaymentTimeout(String orderNo, String createdMessageId);
void cancelExpiredOrder(String orderNo, Long orderId);
```

- `createOrder`、`payOrder` 和 `cancelExpiredOrder` 共用“PREPARED -> 半消息 -> 本地事务 -> COMMITTED -> 提交半消息”的模板。
- 超时调度和超时执行属于订单生命周期，合并回事务版 `OrderService`，不再分别建立两个 Service。
- 本地数据库短事务使用 `TransactionTemplate` 明确包裹，避免同类私有方法调用导致 `@Transactional` 代理失效，也不再需要 `RocketTransactionLocalService`。
- Listener 负责解码和校验消息，只把真正的业务参数传给 Service；超时取消同时传入 `orderNo + orderId`，Service 按订单号重读后交叉校验主键，业务方法不接收 Tag、信封或 `MessageView`。

## 5. 类合并与重命名

| 当前类 | 最终归属 |
|---|---|
| `OutboxOrderService` | `order.outbox.OrderService` |
| `OutboxEventService` | 插入逻辑并入 Outbox `OrderService`；发布状态操作由 Mapper 和 `OutboxRelay` 管理 |
| `RocketTransactionMessageService` | `order.transaction.OrderService` |
| `RocketTransactionLocalService` | 合并为事务 `OrderService` 内由 `TransactionTemplate` 执行的本地事务段 |
| `RocketTransactionTimeoutSchedulerService` | 事务 `OrderService.schedulePaymentTimeout` |
| `RocketTransactionTimeoutExecutorService` | 事务 `OrderService.cancelExpiredOrder` |
| `RocketTransactionRecordService` | `infrastructure.TransactionRecordRepository` |
| `RocketOrderConsumerService` | `order.OrderEventHandler` |
| `RocketProductCacheService` | `product.ProductService` |
| `OutboxPublishScheduler` | `infrastructure.OutboxRelay` |
| `RocketOrderTransactionChecker` | `infrastructure.OrderTransactionChecker` |
| `PreparedTransactionCleanupScheduler` | `infrastructure.PreparedTransactionCleanupTask` |

删除所有旧类，不保留兼容代理或废弃转发方法。

## 6. 为什么仍保留多个 Listener

一个 `@RocketMQMessageListener` Bean 只能对应一套 Topic、Tag 和 ConsumerGroup 配置。缓存、统计、超时调度和超时执行拥有不同消费进度、重试与幂等维度，不能合并成一个 Listener 后在 Java 中自行分流。

Listener 只做三件事：

1. 交给公共支持类解码；
2. 调用明确的订单业务方法；
3. 根据业务是否抛异常向 Broker 返回成功或失败。

它们不是 Service，也不包含订单 SQL。

## 7. HTTP 响应字段精简

两套入口统一返回业务响应 `OrderResponse`：

```text
orderId
orderNo
status
totalAmount
itemCount
```

删除：

- `mechanism`
- `operationType`
- `businessKey`
- `brokerMessageId`
- `transactionState`
- 只用于解释技术步骤的 `message`
- Outbox 事件 ID 列表

这些信息属于日志、Dashboard 和数据库运维查询，不属于用户创建订单的业务响应。

本地订单事务已经提交、但半消息 `commit` RPC 结果不明确时，接口仍返回已持久化的订单业务结果；内部记录告警，并由 Broker 回查收敛，不向业务调用方暴露 RocketMQ `UNKNOWN`。

## 8. 消息字段精简

删除当前通用 `TransactionOrderCommandPayload`，避免 CREATE、PAY、CANCEL 共用一个含大量空字段的 DTO。

使用一个业务命令容器文件定义两种无冗余命令：

```text
CreateOrderCommand：orderNo、userId、items
OrderIdCommand：orderId
```

- CREATE 使用 `CreateOrderCommand`；
- PAY 和 CANCEL 使用 `OrderIdCommand`；
- 每次事务操作只生成一个业务消息 UUID，同时作为信封 `messageId` 和事务记录 `transaction_id`；
- `operationType` 只在事务记录中保存，并由调用路径传入，不复制到业务 payload；
- 消费者不信任命令快照，事务提交后仍以 `mq_transaction_record + orders` 为事实来源。

## 9. 事务记录字段精简

`mq_transaction_record` 最终只保留：

```text
transaction_id
business_type
business_key
operation_type
status
last_error
created_at
updated_at
```

事务记录属于消息协调基础设施，不能出现 `order_no/order_id` 这类订单专用列。通用业务身份由以下两列组成：

- `business_type`：聚合类型；当前订单链固定为 `ORDER`；
- `business_key`：稳定业务键；当前订单链固定使用 `orderNo`。

CREATE、PAY、CANCEL 的 `business_key` 都是同一个订单号，具体操作由 `operation_type` 区分。活跃记录按
`(business_type, business_key, operation_type)` 唯一；`ROLLED_BACK` 保留审计但释放相同业务操作的重试资格。

删除事务表中的 `message_id`：当前一次事务操作只产生一条事务订单事实消息，两个 UUID 没有独立业务含义。最终统一为：

- `transaction_id` 是事务记录主键；
- 同一个值也是事务消息信封的 `messageId`；
- Broker 自己生成的物理 `brokerMessageId` 只进入日志，不保存进事务表；
- 消费者继续使用信封 `messageId`，也就是该 `transaction_id`，进行组内幂等。

事务消息信封的 `aggregateId` 使用事务记录的 `businessKey`；当前 `ORDER` 业务中 CREATE、PAY、CANCEL
一律使用 `orderNo`。不再把 `transactionId` 同时复制到自定义 Header 和 `aggregateId`；Broker 回查直接从
已校验信封的 `messageId` 取得事务记录主键，再校验 `businessType/businessKey`。

事务缓存、统计和超时调度消费者也统一通过信封 `messageId` 查询事务记录，不再把 `aggregateId` 当成事务表主键。

表继续不定义外键；所有表和字段继续提供中文注释；`schema-demo.sql` 只保存最新完整结构，不增加兼容 `ALTER` 迁移。

`order_statistics` 是由消息消费驱动的订单统计投影，业务归属为订单统计，不作为 MQ 基础设施表命名或建模。

## 10. 事务边界

### 10.1 Outbox

Outbox 的 `createOrder`、`payOrder`、`cancelExpiredOrder` 可以直接使用 `@Transactional`，因为它们只修改同一个 PostgreSQL 数据源。后台 `OutboxRelay` 在请求事务之外发送消息。

`OutboxRelay` 使用 `TransactionTemplate` 分别完成“短事务领取”“事务外发送”“短事务回写成功或失败”。网络发送期间不持有候选行锁，也不再依赖一个只为转发 Mapper 方法而存在的 `OutboxEventService`。

### 10.2 RocketMQ 事务消息

事务版 `createOrder` 和 `payOrder` 不能整体添加 `@Transactional`，否则会在发送半消息和提交半消息的网络调用期间长期占用数据库连接和行锁。

实际顺序为：

1. `TransactionRecordRepository` 使用 `REQUIRES_NEW` 短事务写 PREPARED，`transaction_id` 同时作为信封业务 `messageId`；
2. 事务版 `OrderService` 发送半消息；
3. `TransactionTemplate` 开启本地短事务，写订单事实并条件更新 PREPARED -> COMMITTED；
4. 本地事务返回后提交半消息；
5. 失败或结果不确定时，根据持久记录决定 rollback、commit 或等待 Broker 回查。

## 11. 幂等与并发边界

- 创建订单：`orders.order_no` 与活跃 `(ORDER, orderNo, CREATE)` 事务记录唯一约束最终防重。
- 支付和取消：数据库 `WHERE status='PENDING'` 条件更新决定唯一赢家。
- 取消恢复库存：只有成功更新成 CANCELLED 的事务才能恢复库存。
- 消费幂等：继续使用 `(consumer_name, message_id)` 唯一键。
- 缓存删除：允许重复执行，操作天然幂等。
- 统计：消费记录与统计 UPSERT 保持同一 PostgreSQL 事务。
- 超时消息：稳定业务消息 ID 加订单状态条件更新防止重复取消。

## 12. 配置与文档

- Topic、Tag、ConsumerGroup 仍属于部署配置，继续保留在两份 `application*.yaml`。
- 业务方法和业务响应不读取或返回这些技术名称。
- 学习文档按最终包结构重写代码导航，不再引用删除的 Service 和 VO。
- 文档明确：生产系统只启用其中一种可靠消息方案；同时提供两个入口仅用于学习比较。

## 13. 验证边界

遵循项目要求，本次不编写测试、不运行 Maven、不启动应用或容器。实施完成后只执行：

- 删除类和旧字段的引用扫描；
- Java 包名、Mapper 方法和 XML statement 的静态对应检查；
- XML/YAML 语法检查；
- SQL 字段、实体字段和 Mapper 列映射一致性检查；
- `git diff --check`。

保留工作区所有无关修改，不提交、不清理、不覆盖其他模块文件。
