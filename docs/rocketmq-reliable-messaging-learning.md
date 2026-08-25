# RocketMQ 事务消息订单学习案例

## 1. 架构与身份关系

这个案例只保留一条 RocketMQ 事务消息链。PostgreSQL 保存订单、明细、库存和事务裁决事实，RocketMQ 负责把已提交事实投递给缓存失效、统计和付款超时处理器，Redis 只承担商品查询缓存。

```text
HTTP
  -> OrderService
      -> 独立事务写 mq_transaction_record = PREPARED
      -> Broker 保存不可见半消息
      -> PostgreSQL 本地事务写业务事实并推进为 COMMITTED
      -> 本地事务结束后提交半消息
  -> Listener 重读 PostgreSQL 已提交事实
      -> 缓存失效 / 统计投影 / 付款超时调度
```

每次 CREATE、PAY、CANCEL 都只生成一个 UUID，身份关系固定为：

```text
消息 Body = mq_transaction_record.transaction_id
RocketMQ Key = orderNo
RocketMQ Tag = OrderOperation.tag
```

`transactionId` 用于事务回查，以及统计、超时调度等消费防重；`orderNo` 是跨系统稳定订单键。本地数据库 `orderId` 只用于数据库内定位，不替代 `orderNo`。

## 2. HTTP API

### 2.1 查询商品

```http
GET /api/playground/rocketmq/products/{productId}
```

查询采用 Cache Aside：优先读取 Redis，未命中时查询 PostgreSQL 并回填。缓存是允许在 TTL 范围内短暂陈旧的查询快照；并发查询可能在消息删除缓存后回填旧库存，因此是否允许扣减库存始终由 PostgreSQL 条件更新裁决。

### 2.2 创建订单

```http
POST /api/playground/rocketmq/orders
Content-Type: application/json

{
  "userId": 1,
  "orderNo": "ROCKET-LEARN-001",
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 2, "quantity": 1 }
  ]
}
```

### 2.3 支付订单

```http
POST /api/playground/rocketmq/orders/{orderId}/pay
```

创建和支付返回 `Result<OrderResponse>`。订单创建后为 `PENDING`，支付成功后为 `PAID`。没有手工取消接口；付款超时后由延迟消息触发取消。

## 3. 最终目录

Java 代码统一位于 `com.xt.xiaoxingxing.playground.rocketmq`，按技术职责分层：

```text
rocketmq
├── checker
│   └── OrderTransactionChecker.java
├── config
│   ├── OrderMqConstants.java
│   ├── OrderMqConfig.java
│   ├── OrderMqProperties.java
│   └── OrderMqListenerEnhancer.java
├── controller
│   ├── OrderController.java
│   └── ProductController.java
├── dto
│   ├── CreateOrderItemRequest.java
│   ├── CreateOrderRequest.java
│   ├── OrderResponse.java
│   └── ProductResponse.java
├── enums
│   ├── MqTransactionStatus.java
│   ├── OrderOperation.java
│   └── OrderStatus.java
├── entity
│   ├── MqConsumedMessage.java
│   ├── MqTransactionRecord.java
│   ├── Order.java
│   ├── OrderItem.java
│   └── Product.java
├── listener
│   ├── OrderStatisticsListener.java
│   ├── PaymentTimeoutListener.java
│   ├── PaymentTimeoutScheduleListener.java
│   └── ProductCacheInvalidationListener.java
├── mapper
│   ├── MqConsumerRecordMapper.java
│   ├── MqTransactionRecordMapper.java
│   ├── OrderMapper.java
│   └── OrderStatisticsMapper.java
├── util
│   ├── RocketMessageCodec.java
│   └── RocketMqUtil.java
├── repository
│   └── TransactionRecordRepository.java
├── service
│   ├── OrderService.java
│   ├── OrderStatisticsService.java
│   ├── PaymentTimeoutScheduler.java
│   └── ProductService.java
└── task
    └── PreparedTransactionCleanupTask.java
```

MyBatis 资源只有四个：

```text
src/main/resources/mapper/rocketmq
├── MqConsumerRecordMapper.xml
├── MqTransactionRecordMapper.xml
├── OrderMapper.xml
└── OrderStatisticsMapper.xml
```

## 4. Topic、Tag、ConsumerGroup 与订阅

Topic 表示消息大类，Tag 表示 Topic 内的事件类型，ConsumerGroup 表示一项独立消费职责。不同组各消费一份；统计和超时调度使用数据库幂等键，缓存删除依靠重复 delete 的天然幂等。

### 4.1 两个 Topic

| 常量 | 实际值 | Broker 消息类型 | 用途 |
|---|---|---|---|
| `TOPIC_TRANSACTION` | `order_transactions` | `TRANSACTION` | CREATE、PAY、CANCEL 事务消息 |
| `TOPIC_DELAY` | `order_delay` | `DELAY` | 付款超时检查延迟消息 |

RocketMQ 5.x 会校验 Topic 的消息类型，不能把这两个 Topic 当作普通 `NORMAL` Topic 创建。当前
`docker-compose.yml` 的 `rocketmq-init` 已使用 `mqadmin updateTopic` 分别写入
`+message.type=TRANSACTION` 和 `+message.type=DELAY`。

### 4.2 四个 Tag

| 常量 | 实际值 | 含义 |
|---|---|---|
| `TAG_ORDER_CREATED` | `order_created` | 订单已创建、明细已写入、库存已扣减 |
| `TAG_ORDER_PAID` | `order_paid` | 订单已支付 |
| `TAG_ORDER_CANCELLED` | `order_cancelled` | 订单已取消、库存已恢复 |
| `TAG_PAYMENT_TIMEOUT_CHECK` | `payment_timeout_check` | 到期后检查是否仍需取消 |

Tag 本身就是事件类型，不再在消息 Body 中重复传一份 `eventType`。Listener 注解中的两个组合订阅表达式为：

```text
缓存组: order_created || order_cancelled
统计组: order_created || order_paid || order_cancelled
```

### 4.3 四个 ConsumerGroup

| 实际值 | Topic / Tag | Listener | 副作用 |
|---|---|---|---|
| `order_cache_group` | `order_transactions` / 缓存组合订阅 | `ProductCacheInvalidationListener` | 删除库存变化商品的 Redis 缓存 |
| `order_statistics_group` | `order_transactions` / 统计组合订阅 | `OrderStatisticsListener` | 更新订单统计投影 |
| `timeout_scheduler_group` | `order_transactions` / `order_created` | `PaymentTimeoutScheduleListener` | 发送付款超时延迟消息 |
| `order_timeout_group` | `order_delay` / `payment_timeout_check` | `PaymentTimeoutListener` | 到期后尝试取消订单 |

这些名称直接表达业务职责，不再使用 `pg_` 项目前缀，也不把 `v1/v2` 版本号写入 Topic 或 ConsumerGroup。

## 5. CREATE、PAY、CANCEL 事务流程

### 5.1 共同模板

`OrderService` 的三种操作共用同一可靠性顺序：

`OrderOperation` 只维护一份 `CREATE/PAY/CANCEL -> order_created/order_paid/order_cancelled` 映射。映射出的值直接作为 Broker Tag；消息 Body 不再重复保存事件类型。

1. `TransactionRecordRepository.prepare` 使用 `REQUIRES_NEW` 独立提交 `PREPARED`；
2. `RocketMqUtil.sendTransaction` 让 Broker 保存不可见半消息；
3. `TransactionTemplate` 执行业务 SQL，并在同一 PostgreSQL 本地事务中条件推进 `PREPARED -> COMMITTED`；
4. 退出数据库事务后再调用半消息 `commit()`；
5. 若结果不明确，不反向修改已经提交的订单事实，交给 Broker Checker 根据持久状态继续裁决。

`markCommitted` 使用 `MANDATORY`。因此业务数据和 `COMMITTED` 必须一起提交或一起回滚，半消息提交不在数据库事务内部。

### 5.2 CREATE

CREATE 的本地事务完成以下动作：

1. 检查用户和商品；
2. 按商品 ID 合并重复明细；
3. 使用数据库商品价格计算金额；
4. 插入 `PENDING` 订单和 `order_products` 明细；
5. 使用 `stock >= quantity` 条件扣减库存；
6. 将事务记录推进为 `COMMITTED`。

任何一步失败，订单、明细、库存和 `COMMITTED` 一起回滚。消息可见后，缓存组、统计组和超时调度组分别处理同一个 CREATE 事实。

### 5.3 PAY

支付入口通过 `orderId` 读取 `orderNo`。本地 SQL 只允许 `PENDING -> PAID`，条件更新决定支付与取消的并发结果。事务表只保存消息事务状态，订单幂等和状态约束由订单表负责。

### 5.4 CANCEL

取消由付款超时消费者触发。超时消息只用 RocketMQ Key 携带 `orderNo`，Service 据此查询订单，不再同时传 `orderId` 做双重核对。只有订单仍为 `PENDING` 才执行 `PENDING -> CANCELLED` 并按明细恢复库存，支付和取消并发时只有一个条件更新能够成功。

## 6. Broker Checker 与过期 PREPARED 清理

`OrderTransactionChecker` 直接读取消息 Body 中的 `transactionId`，按事务表主键查询持久状态。普通消费者不再重复查询事务表证明消息已提交；事务消息只有 Broker 收到 COMMIT 后才会投递，事务状态核对只保留在真正负责回查裁决的 Checker 中。

裁决规则如下：

| 持久状态 | Checker 结果 |
|---|---|
| `COMMITTED` | `COMMIT` |
| `ROLLED_BACK` | `ROLLBACK` |
| 保护窗口内的 `PREPARED` | `UNKNOWN`，等待下次回查 |
| 超过保护窗口的 `PREPARED` | 先条件更新为 `ROLLED_BACK`，成功后返回 `ROLLBACK` |

条件更新为 0 行时必须重读并尊重并发赢家，不能覆盖已经提交的状态。

还有一个 Broker 无法发现的窗口：`PREPARED` 已提交，但应用在半消息到达 Broker 前退出。`PreparedTransactionCleanupTask` 扫描超过保护窗口的记录，并通过相同的条件更新收口；它不会无条件覆盖 `COMMITTED`。

## 7. 付款超时与消费幂等

CREATE 消息提交后，`PaymentTimeoutScheduleListener` 只解析 Key 与 Body，再交给 `PaymentTimeoutScheduler` 分三段安排延迟检查：

1. 短只读事务确认订单仍为 `PENDING` 且该 CREATE 消息尚未调度；
2. 在数据库事务外发送 DELAY 消息；
3. Broker 明确接收后，用短事务写入消费完成记录。

延迟消息 Body 直接沿用来源 CREATE 的 `transactionId`。超时取消使用 Key 中的 `orderNo` 定位订单。

`mq_consumed_message` 使用以下唯一键：

```text
(consumer_group, consume_id)
```

因此同一消息可以被不同职责分别处理，但各职责的幂等边界并不相同：

- 缓存失效只删除 Redis，不再为天然幂等的删除额外登记数据库记录；
- 统计处理把幂等 INSERT 与 `order_statistics` UPSERT 放在同一事务，因此同组重复投递只累计一次；
- 超时调度在延迟消息发送成功后登记完成。发送与登记无法组成跨系统原子事务，并发投递或发送成功后进程退出时可能产生重复 DELAY 消息，但不会因为提前登记而漏发；
- 延迟消息到期后再次读取订单，非 `PENDING` 状态直接按幂等成功结束。

CREATE、PAY、CANCEL 的事务半消息 Body 只放 `transactionId`。延迟检查 Body 沿用 CREATE 的 `transactionId`。消费者按 RocketMQ Key 中的 `orderNo` 查询订单数据。

## 8. 数据表

当前案例使用以下表：

| 表 | 职责 |
|---|---|
| `users` | 下单用户 |
| `products` | 商品、价格和权威库存 |
| `orders` | 订单主表与状态 |
| `order_products` | 订单明细与成交单价快照 |
| `mq_transaction_record` | 半消息对应的持久事务裁决状态 |
| `mq_consumed_message` | 统计与超时调度等非天然幂等职责的消费记录 |
| `order_statistics` | 消息驱动的订单统计投影 |

建表脚本位于 `docs/schema-demo.sql`。MQ 相关表不使用数据库外键，依靠稳定业务值逻辑关联，避免清理业务数据时破坏消息审计记录。

## 9. 配置与运行提示

### 9.1 YAML 只保留连接和真实环境覆盖

开发环境在 `application-dev.yaml` 中配置本地连接：

```yaml
rocketmq:
  producer:
    endpoints: localhost:18081
    access-key: ""
    secret-key: ""
    namespace: ""
    ssl-enabled: false

```

`endpoints` 指向 RocketMQ Proxy 的 gRPC 端口。当前本地 Proxy 不启用 TLS，因此显式关闭 SSL。Producer 直接读取该配置；Starter 2.3.6 不能在 Listener 注解的 boolean 属性中解析 YAML 占位符，所以 `OrderMqListenerEnhancer` 只负责把同一个 `ssl-enabled` 值同步给四个 Listener。切换 TLS 环境时只需改这一处连接配置。当前案例没有半开半关的运行模式，因此不再保留只能关闭 Listener、却无法关闭发送入口的 `enabled` 假开关。

同一文件还会缩短开发环境的付款超时时间：

```yaml
playground:
  order-mq:
    order-timeout-millis: 100000
```

Producer 不再在 YAML 重复声明请求超时，使用 RocketMQ SDK 默认的 3 秒。

正式环境在 `application-prod.yaml` 中通过 `ROCKETMQ_ENDPOINTS`、`ROCKETMQ_ACCESS_KEY`、
`ROCKETMQ_SECRET_KEY`、`ROCKETMQ_NAMESPACE` 和 `ROCKETMQ_SSL_ENABLED` 环境变量提供连接参数。

### 9.2 Java 默认值

`OrderMqProperties` 只保留四个确实需要按环境调整的业务默认值：

| 属性 | 默认值 |
|---|---:|
| `orderTimeoutMillis` | `1_800_000` 毫秒 |
| `transactionPreparedTimeoutSeconds` | `120` 秒 |
| `transactionCleanupBatchSize` | `50` 条 |
| `productCacheTtlSeconds` | `300` 秒 |

Broker 最小延迟固定为 1 秒，商品缓存前缀固定为 `playground:product:`，都直接作为代码常量。过期 PREPARED 清理任务的首次延迟和固定间隔也固定为 `30000` 毫秒，不再制造没有实际覆盖需求的 YAML 配置入口。

### 9.3 最小运行顺序

1. 启动 PostgreSQL、Redis、RocketMQ Broker 与 Proxy；
2. 在 Playground 数据库执行 `docs/schema-demo.sql`；
3. 确认 Broker 已有 `order_transactions`、`order_delay` 两个 Topic，且消息类型分别为 `TRANSACTION`、`DELAY`；
4. 启动应用，先查询商品，再创建订单并观察库存、事务记录和消费记录；
5. 支付订单，或等待开发环境的 100 秒超时检查观察取消流程。

排查时优先查看：

```sql
SELECT transaction_id, status, last_error, created_at, updated_at
FROM mq_transaction_record
ORDER BY created_at DESC;

SELECT consumer_group, consume_id, consumed_at
FROM mq_consumed_message
ORDER BY consumed_at DESC;

SELECT id, created_count, paid_count, cancelled_count,
       created_amount, last_consumed_at, updated_at
FROM order_statistics;
```

这套设计提供的是至少一次投递效果：生产端使用 `transactionId` 记录事务状态，消费端将该值写入消费记录的 `consume_id` 字段进行防重。
