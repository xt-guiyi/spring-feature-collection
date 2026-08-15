# RocketMQ 可靠订单消息学习案例

## 1. 先明确这个案例要解决什么

用户下单同时包含两类事实：

1. PostgreSQL 订单、订单明细和库存，这是权威业务事实；
2. RocketMQ 订单事件，用来驱动缓存失效、统计投影和付款超时取消。

困难不在于“会调用发送 API”，而在于数据库和 Broker 不能参加同一个普通本地事务：

- 数据库提交后应用崩溃，消息可能没有发送；
- 消息已发送但回包丢失，应用可能重复发送；
- 消费者完成业务后、确认消息前崩溃，同一消息会再次投递。

本 Playground 用同一套订单业务并排演示两种解决方案：

- Transactional Outbox：订单事实和消息意图先在同一个 PostgreSQL 事务落库，再由 `OutboxRelay` 可靠发布；
- RocketMQ 事务消息：Broker 先保存不可见半消息，本地事务完成后提交半消息，并由事务回查收敛不确定结果。

> 学习项目同时保留两套入口是为了逐步骤对照。真实生产系统的同一项订单业务应选择其中一种，不要同时写 Outbox 又发送事务消息，否则会得到两份语义相同的事件。

两种机制都只提供至少一次效果，消费者幂等仍然是必需的。代码没有宣传“端到端恰好一次”。

## 2. 五个业务 HTTP 入口

### 2.1 查询商品并观察 Cache Aside

```http
GET /api/playground/rocketmq/products/{productId}
```

第一次查询通常从 PostgreSQL 读取并回填 Redis，随后查询可命中 Redis。响应中的 `cacheHit` 只表示本次数据来源；库存扣减的最终裁决始终在 PostgreSQL。

### 2.2 Outbox 创建、支付订单

```http
POST /api/playground/rocketmq/outbox/orders
POST /api/playground/rocketmq/outbox/orders/{orderId}/pay
```

### 2.3 事务消息创建、支付订单

```http
POST /api/playground/rocketmq/transaction/orders
POST /api/playground/rocketmq/transaction/orders/{orderId}/pay
```

创建请求示例：

```json
{
  "userId": 1,
  "orderNo": "ROCKET-LEARN-20260815-001",
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 2, "quantity": 1 }
  ]
}
```

两套创建、支付入口统一返回 `Result<OrderResponse>`。核心 `data` 形状如下：

```json
{
  "orderId": 101,
  "orderNo": "ROCKET-LEARN-20260815-001",
  "status": "PENDING",
  "totalAmount": 239.70,
  "itemCount": 3
}
```

支付成功时 `status` 为 `PAID`。响应只包含订单 ID、订单号、状态、金额和商品总件数；消息身份与事务裁决状态都是内部可靠性实现，不应污染订单 API。

没有手工“取消订单”HTTP 接口。两套方案都由付款超时消息触发取消，取消时再次检查订单是否仍为 `PENDING`。

## 3. 最终代码导航

### 3.1 共同契约与基础设施

- `rocketmq/message/RocketMessageEnvelope.java`：版本化消息信封；
- `rocketmq/message/OrderEventPayload.java`：已发生订单事实的事件负载；
- `rocketmq/message/OrderTransactionCommands.java`：事务半消息执行本地操作所需的最小命令；
- `rocketmq/support/RocketMessageCodec.java`：编码、解码和协议校验；
- `rocketmq/support/RocketMessagePublisher.java`：同步 NORMAL、DELAY、TRANSACTION 发布；
- `rocketmq/support/RocketConsumerSupport.java`：统一解码、取得真实 Tag、调用业务处理器并返回消费结果；
- `rocketmq/order/OrderResponse.java`：两套入口共用的订单响应；
- `rocketmq/order/OrderEventHandler.java`：缓存失效与统计投影；
- `rocketmq/product/ProductController.java`、`ProductService.java`：Cache Aside 商品查询。

### 3.2 Outbox 链路

- `rocketmq/order/outbox/OrderController.java`；
- `rocketmq/order/outbox/OrderService.java`；
- `rocketmq/infrastructure/OutboxRelay.java`；
- `rocketmq/order/outbox/listener/ProductCacheInvalidationListener.java`；
- `rocketmq/order/outbox/listener/OrderStatisticsListener.java`；
- `rocketmq/order/outbox/listener/PaymentTimeoutListener.java`。

### 3.3 事务消息链路

- `rocketmq/order/transaction/OrderController.java`；
- `rocketmq/order/transaction/OrderService.java`；
- `rocketmq/infrastructure/TransactionRecordRepository.java`；
- `rocketmq/infrastructure/OrderTransactionChecker.java`；
- `rocketmq/infrastructure/PreparedTransactionCleanupTask.java`；
- `rocketmq/order/transaction/listener/ProductCacheInvalidationListener.java`；
- `rocketmq/order/transaction/listener/OrderStatisticsListener.java`；
- `rocketmq/order/transaction/listener/PaymentTimeoutScheduleListener.java`；
- `rocketmq/order/transaction/listener/PaymentTimeoutListener.java`。

两个包里存在同名 Listener，但显式 Spring Bean 名不同，不会发生 Bean 名冲突。

## 4. Topic、Tag 和 ConsumerGroup 的真实关系

Topic 是消息的大类，Tag 是 Topic 内的二级业务过滤条件，ConsumerGroup 表示一项独立消费职责。同组多实例竞争消费，不同组各得到一份消息。

### 4.1 三个 Topic

| 配置键 | 固定值 | 用途 |
|---|---|---|
| `topics.normal` | `pg_order_events` | Outbox 创建、支付、取消事实事件 |
| `topics.delay` | `pg_order_timeouts` | 两套方案的付款超时检查 |
| `topics.transaction` | `pg_order_transactions` | 事务消息 CREATE、PAY、CANCEL 半消息 |

### 4.2 五个 Tag

| 固定值 | 业务含义 |
|---|---|
| `ORDER_CREATED` | 订单已创建且库存已扣减 |
| `ORDER_PAID` | 订单已支付 |
| `ORDER_CANCELLED` | 订单已取消且库存已恢复 |
| `OUTBOX_PAYMENT_TIMEOUT_CHECK` | Outbox 订单付款超时检查 |
| `TRANSACTION_PAYMENT_TIMEOUT_CHECK` | 事务消息订单付款超时检查 |

### 4.3 七个消费组和七个 Listener

| ConsumerGroup 固定值 | 监听 Topic / Tag | Listener | 真实副作用 |
|---|---|---|---|
| `pg_outbox_order_cache_group_v1` | normal / CREATED、CANCELLED | outbox `ProductCacheInvalidationListener` | 删除变更库存商品的 Redis 缓存 |
| `pg_outbox_order_statistics_group_v1` | normal / CREATED、PAID、CANCELLED | outbox `OrderStatisticsListener` | 更新订单统计 |
| `pg_outbox_order_timeout_group_v1` | delay / OUTBOX timeout | outbox `PaymentTimeoutListener` | 超时后尝试取消 Outbox 订单 |
| `pg_transaction_order_cache_group_v1` | transaction / CREATED、CANCELLED | transaction `ProductCacheInvalidationListener` | 从数据库恢复已提交事实后删除缓存 |
| `pg_transaction_order_statistics_group_v1` | transaction / CREATED、PAID、CANCELLED | transaction `OrderStatisticsListener` | 从数据库恢复已提交事实后更新统计 |
| `pg_transaction_timeout_scheduler_group_v1` | transaction / CREATED | transaction `PaymentTimeoutScheduleListener` | 安排事务订单延迟检查 |
| `pg_transaction_order_timeout_group_v1` | delay / TRANSACTION timeout | transaction `PaymentTimeoutListener` | 超时后尝试事务取消 |

缓存组不订阅 `ORDER_PAID`，因为支付不改变商品库存。统计组需要订阅三种订单事实。

## 5. Outbox：先落消息意图，再可靠发布

### 5.1 创建订单

`order.outbox.OrderService#createOrder` 在同一个 PostgreSQL 本地事务中完成：

1. 校验用户、商品和数量；
2. 创建 `PENDING` 订单与明细；
3. 使用带 `stock >= quantity` 条件的 SQL 扣减库存；
4. 插入 `ORDER_CREATED` Outbox 事件；
5. 插入 `OUTBOX_PAYMENT_TIMEOUT_CHECK` Outbox 事件，保存未来 `deliverAt`；
6. 一起提交，任何一步失败全部回滚。

这里不存在“订单提交成功但消息意图没有保存”的窗口。

### 5.2 OutboxRelay 的三个短窗口

`OutboxRelay#publishPendingEvents` 每轮按以下顺序工作：

1. 短事务用 `FOR UPDATE SKIP LOCKED` 原子领取 `PENDING/FAILED` 或租约过期的 `PROCESSING`；
2. 提交领取事务，释放数据库连接和行锁；
3. 在数据库事务外同步调用 RocketMQ；普通事件发 NORMAL，超时事件按 `deliverAt` 计算 DELAY；
4. Broker 明确成功后，用 `id + lockedAt` 在新短事务标记 `PUBLISHED`；
5. 失败则在新短事务递增重试次数、指数退避，超过上限转为 `DEAD`。

若 Broker 已接收，但进程在回写 `PUBLISHED` 前崩溃，租约过期后会再次发布。同一 Outbox `id` 同时也是信封中的稳定 `messageId`，消费者用它幂等。

### 5.3 支付和超时取消

- `OrderService#payOrder` 只允许 `PENDING -> PAID`，状态更新与 `ORDER_PAID` Outbox 事件同事务；
- `PaymentTimeoutListener` 收到延迟检查后调用 `OrderService#cancelExpiredOrder`；
- 取消只允许 `PENDING -> CANCELLED`，并在同一事务恢复库存、写 `ORDER_CANCELLED` Outbox 事件；
- 支付与取消同时到达时，条件 UPDATE 的受影响行数决定唯一赢家，不依赖先查到的旧状态。

## 6. RocketMQ 事务消息：半消息协调本地事务

### 6.1 唯一身份和真实聚合键

每次 CREATE、PAY、CANCEL 只生成一个 UUID，并满足：

```text
transactionId = mq_transaction_record.transaction_id = envelope.messageId
```

`aggregateId` 不承担事务记录主键职责，只表达稳定业务聚合。事务记录用通用字段描述业务对象：

```text
businessType = ORDER
businessKey = orderNo
aggregateId = businessKey = orderNo
```

因此当前订单业务的 CREATE、PAY、CANCEL 都使用同一个 `orderNo` 作为聚合键。支付和取消的 HTTP
入口虽然接收本地 `orderId`，但 Service 会先重读订单取得 `orderNo`，再准备事务记录和构造消息；本地数据库
主键不能替代跨系统稳定业务键。

`mq_transaction_record` 不复制第二份消息 UUID，也不依赖自定义 Header。Broker Checker 解码信封后，直接用
`envelope.messageId` 查询事务记录，再校验 `businessType/businessKey` 与信封聚合键。

### 6.2 CREATE、PAY、CANCEL 的共同执行模板

`order.transaction.OrderService#executeTransaction` 的关键顺序是：

1. `TransactionRecordRepository` 用 `REQUIRES_NEW` 独立提交 `PREPARED`；
2. 在 PostgreSQL 事务外调用 `RocketMessagePublisher#beginTransaction`，让 Broker 保存不可见半消息；
3. `TransactionTemplate` 只包业务 SQL和 `PREPARED -> COMMITTED` 条件更新；
4. `markCommitted` 使用 `MANDATORY`，所以订单/库存事实和 `COMMITTED` 必须同事务提交或回滚；
5. 退出数据库事务后调用半消息 `commit()`。

CREATE 本地事务创建订单、明细并扣库存；PAY 条件更新为 `PAID`；CANCEL 条件更新为 `CANCELLED` 并只恢复一次库存。

如果本地代码抛异常，Service 不能看到 Java 异常就盲目回滚半消息。它先重读事务记录：

- `COMMITTED`：数据库事实已经成功，提交半消息并从数据库恢复 `OrderResponse`；
- `ROLLED_BACK`：回滚半消息；
- `PREPARED`：只有条件抢到 `ROLLED_BACK` 后才回滚半消息；
- 状态查询也不明确：不猜测终态，保留给 Broker 回查。

数据库已经提交后，即使 `commit()` RPC 超时或连接断开，HTTP 仍返回真实 `OrderResponse`。因为订单不能反向回滚，Broker 将通过 Checker 再次确认 `COMMITTED`。

### 6.3 Broker 回查和 PREPARED 孤儿

`OrderTransactionChecker#check`：

1. 校验信封并以 `envelope.messageId` 查询事务记录；
2. 同时校验 `businessType=ORDER`、`businessKey`、`operationType`、`eventType`，并确认
   `aggregateId = businessKey`；
3. `COMMITTED` 返回 COMMIT，`ROLLED_BACK` 返回 ROLLBACK；
4. 保护窗口内 `PREPARED` 返回 UNKNOWN；
5. 超时 `PREPARED` 先条件抢占 `ROLLED_BACK`；更新 0 行必须重读并尊重并发赢家。

还有一个 Broker 无法主动发现的窗口：数据库已经提交 `PREPARED`，但进程在半消息到达 Broker 前崩溃。`PreparedTransactionCleanupTask` 只扫描超过保护窗口的孤儿候选，并逐条用相同条件终态更新收口；它不能覆盖已经提交的订单事实。

### 6.4 事务版超时调度为什么拆成三个阶段

`PaymentTimeoutScheduleListener` 收到已提交 CREATE 消息后调用
`OrderService#schedulePaymentTimeout(orderNo, createdMessageId)`：

1. 短只读事务：检查该 CREATE 消息是否已完成调度、校验 `COMMITTED` 记录与订单事实，并构造计划；
2. 事务外：用由 `createdMessageId` 确定性派生的稳定 timeout messageId 同步发送 DELAY；
3. 短写事务：只有 Broker 明确成功后才写调度消费完成记录。

不能先写“已调度”再发送，否则发送失败后重投会被完成记录挡住，消息将永久漏发。若发送成功但完成记录提交或回包不明确，后续会用同一个稳定 timeout messageId 重发；这是“允许重复，不能漏发”。延迟消费者与 CANCEL 条件更新负责最终幂等。

延迟消息到期后，`PaymentTimeoutListener` 会同时提取 `orderNo + orderId`。事务版 `OrderService` 按
`orderNo` 重读权威订单，再确认数据库主键等于消息中的 `orderId`；两者不是同一订单时直接失败，不能修改任何订单。

事务半消息中的 CREATE/PAY/CANCEL 正文是执行本地操作的命令，不是可直接相信的订单事实。事务版缓存、统计
Listener 会先确认事务记录为 `COMMITTED`，校验 `businessType=ORDER`、`aggregateId=businessKey` 和操作类型，
再用 `businessKey/orderNo` 从 PostgreSQL 重读订单及明细，组装真实 `OrderEventPayload`。

## 7. 消费幂等与两个真实副作用

### 7.1 `mq_consumed_message.message_id` 为什么必须保留

`mq_transaction_record` 删除重复的消息 ID 列，不代表消费表也删除。`mq_consumed_message.message_id` 是每个消费组的幂等键，唯一约束为：

```text
(consumer_name, message_id)
```

同一条订单消息可以由缓存组和统计组各处理一次；同组重复投递只能首次产生副作用。

### 7.2 缓存失效：先查、Redis delete、短写幂等

`OrderEventHandler#invalidateProductCache` 的顺序是：

1. 短只读事务检查幂等记录；
2. 退出数据库事务后删除 Redis Key；
3. 删除成功后，用短事务插入消费完成记录。

Redis 与 PostgreSQL 没有共同本地事务，不能先写完成记录再删缓存。当前顺序若在 delete 后崩溃，Broker 重投只会再次 delete，天然安全。

这里使用的是 Cache Aside：查询时先读 Redis，未命中再读 PostgreSQL 并回填；库存变化后通过事件删除缓存。它存在一个可接受但必须知道的并发边界：

```text
查询线程读到旧数据库快照
→ 订单事务更新库存并删除缓存
→ 查询线程在删除之后把旧快照回填 Redis
```

这会造成 TTL 时间内的短暂陈旧。因此缓存不能裁决是否可以扣库存，真正下单必须依赖 PostgreSQL 条件 UPDATE。若业务要求更强一致性，可叠加版本号、延迟双删或 CDC 等方案，但复杂度会更高。

### 7.3 统计投影：幂等记录与 UPSERT 同事务

`OrderEventHandler#recordStatistics` 在同一个 PostgreSQL 短事务内：

1. `INSERT ... ON CONFLICT DO NOTHING` 竞争幂等键；
2. 插入 0 行表示已经处理，直接结束；
3. 插入 1 行才 UPSERT `order_statistics`；
4. 统计失败时幂等记录一起回滚，使 Broker 重投仍可重新处理。

## 8. 三张 MQ 基础设施表与订单统计投影分别负责什么

| 表 | 职责 |
|---|---|
| `mq_outbox_event` | Outbox 消息意图、租约、重试和最终发布状态 |
| `mq_consumed_message` | 各消费组的消息幂等记录，也记录事务超时调度完成 |
| `order_statistics` | 由消息消费驱动的创建、支付、取消事件订单统计投影 |
| `mq_transaction_record` | 事务半消息的持久裁决依据 |

`mq_transaction_record` 最终只包含 8 个字段：

```text
transaction_id, business_type, business_key, operation_type,
status, last_error, created_at, updated_at
```

数据库约束表达以下不变量：

- `business_type` 和 `business_key` 是基础设施可复用的通用业务身份，不在事务表中增加订单专用列；
- 当前订单链固定 `business_type=ORDER`，CREATE/PAY/CANCEL 的 `business_key` 都是 `orderNo`；
- 同一个订单的不同操作由 `operation_type` 区分；
- 活跃记录按 `(business_type, business_key, operation_type)` 唯一；
- 只有 `PREPARED/COMMITTED` 占用活跃唯一键，`ROLLED_BACK` 保留审计但允许新 UUID 重试。

这些表均不定义数据库外键。订单、消息、消费记录通过业务值逻辑关联，避免消息审计数据因业务表清理而丢失。

## 9. PostgreSQL 排查 SQL

### 9.1 查看订单和库存事实

```sql
SELECT id, order_no, user_id, total_amount, status, created_at
FROM orders
ORDER BY id DESC;

SELECT id, name, price, stock, status, updated_at
FROM products
ORDER BY id;
```

### 9.2 查看 Outbox 积压、失败和死信

```sql
SELECT id, aggregate_id, event_type, topic_name, message_tag,
       status, retry_count, next_retry_at, locked_at,
       last_error, created_at, published_at
FROM mq_outbox_event
ORDER BY created_at DESC, id DESC;
```

重点观察：

- `PENDING/FAILED` 是否长期不再变化；
- `PROCESSING` 的租约是否已经过期；
- `DEAD` 的 `last_error` 是否指出配置或协议错误；
- Broker 成功后是否进入 `PUBLISHED`。

### 9.3 查看消费幂等和统计

```sql
SELECT consumer_name, message_id, event_type, aggregate_id, consumed_at
FROM mq_consumed_message
ORDER BY consumed_at DESC, id DESC;

SELECT id, created_count, paid_count, cancelled_count,
       created_amount, last_event_at, updated_at
FROM order_statistics;
```

这里的 `mq_consumed_message.message_id` 是合法且必需的消费幂等字段。

### 9.4 查看事务消息裁决

```sql
SELECT transaction_id, business_type, business_key, operation_type,
       status, last_error, created_at, updated_at
FROM mq_transaction_record
ORDER BY created_at DESC, transaction_id DESC;
```

如果长期存在 `PREPARED`，依次确认保护窗口、Checker 日志和 `PreparedTransactionCleanupTask` 是否运行。不要人工只改 Broker 状态而不核对 PostgreSQL 订单事实。

## 10. Dashboard 应该观察什么

RocketMQ Dashboard 用于观察 Topic、ConsumerGroup、消费进度、重试和死信，不是业务事实来源：

1. `pg_order_events` 是否有 Outbox 三类事实事件；
2. `pg_order_transactions` 是否有事务消息以及对应消费组进度；
3. `pg_order_timeouts` 是否出现两种超时 Tag；
4. 七个消费组是否在线、是否积压；
5. 重试或 DLQ 消息的 Tag、Key、Broker messageId 和错误日志。

应用 `messageId` 用于端到端幂等，Broker messageId 用于 Dashboard 排查一次物理投递，两者不要混为一谈。

## 11. 必须理解的故障场景

### Broker 故障

- Outbox：订单和消息意图仍可同事务提交；Relay 失败后指数退避，最终成功或进入 DEAD；
- 事务消息：半消息未发出时本次业务失败并将 PREPARED 收口；半消息已到 Broker但回包不明时依赖回查。

### 重复投递

- 缓存 delete 可重复；成功后以 `(consumer_name,message_id)` 记录完成；
- 统计的幂等 INSERT 与 UPSERT 同事务；
- 订单状态和库存恢复还由条件 UPDATE 兜底。

### 支付与取消竞争

两个线程都可能先读到 `PENDING`，但只有一个条件 UPDATE 可以成功。支付成功后超时检查只读到 `PAID` 并结束；取消先成功则支付不能再次推进状态，库存只恢复一次。

### 回查与 UNKNOWN

Checker 只信任持久化事务记录。记录不存在、协议不匹配或保护窗口内 PREPARED 都返回 UNKNOWN，不凭内存或异常文本猜测。

### PREPARED 孤儿

Broker 收到半消息时由 Checker 处理；Broker 根本没收到半消息时由 `PreparedTransactionCleanupTask` 处理。两者都以 `PREPARED -> 终态` 条件更新竞争，不覆盖 COMMITTED。

### 重试多次仍失败和 DLQ

Outbox 发布侧有自己的 `DEAD` 状态；消费者侧持续失败由 RocketMQ 重试和 DLQ 承接。修复根因后再人工重放，重放仍使用原稳定业务 messageId，不能绕过幂等键制造第二次副作用。

## 12. 消息顺序边界

本案例没有承诺强 FIFO：

- 缓存和统计是不同消费组，本来就独立推进；
- 多实例、重试和网络抖动都可能改变到达顺序；
- 消费者以数据库当前状态、事务记录和幂等键处理消息，不假设“创建一定紧挨着支付”；
- 付款超时消息只负责未来重新检查，不能收到就无条件取消。

如果某项新业务真的依赖严格顺序，应先明确聚合键、分区策略、并发度和失败补偿，再单独设计；不要把普通可靠消息案例误认为自动具备全局顺序。

## 13. 推荐学习顺序

1. 先查询同一商品两次，观察 Cache Aside 的数据库回填和 Redis 命中；
2. 使用 Outbox 创建订单，查看订单、库存和两条 Outbox 意图同事务出现；
3. 观察 Relay 的领取、事务外发送和短事务回写；
4. 支付订单并观察支付与超时取消的条件竞争；
5. 使用事务消息入口创建订单，跟踪 PREPARED、半消息、COMMITTED、commit 与 Checker；
6. 对照七个消费组，确认缓存、统计、超时调度各自只承担一个真实职责；
7. 最后模拟 Broker 不可用、重复投递、消费异常和 PREPARED 孤儿，结合 Dashboard 与四张表判断系统为什么没有静默丢失副作用。
