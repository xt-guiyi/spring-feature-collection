# RabbitMQ 可靠消息学习模块设计

## 1. 目标

在 `com.xt.xiaoxingxing.playground.rabbitmq` 下新增一个独立、可学习、可观察的 RabbitMQ 模块，统一入口为
`/api/playground/rabbitmq/**`。模块既覆盖 Direct、Topic、Fanout 等基础路由，也使用 PostgreSQL
Transactional Outbox、Publisher Confirm、Mandatory Return、消费者手动确认、重试、死信、延迟消息和消费幂等，
完成一条可靠订单消息链路。

本模块是第一次学习 RabbitMQ 的案例，因此 Controller、配置、生产者、消费者、Service、Mapper XML 和 SQL
都需要中文注释。复杂方法先列出完整步骤，再使用“第1步、第2步……”实现；注释重点解释失败窗口和设计原因，
不能只把代码翻译成中文。

## 2. 项目约束

- Java 21、Spring Boot 4.1.0、Spring AMQP 4.1.x。
- PostgreSQL 使用现有 `playgroundDataSource` 和 `playgroundTransactionManager`。
- 创建订单复用现有 `PgMyBatisService.createCompleteOrder`，不得复制订单金额和库存扣减逻辑。
- Redis 只用于演示删除商品缓存，消费记录和订单统计保存在 PostgreSQL。
- 不使用 PostgreSQL 与 RabbitMQ 的分布式事务。
- 不安装 RabbitMQ Delayed Message Plugin；固定延迟使用 TTL + DLX。
- 不编写测试文件，不运行测试或 Maven 构建；只做静态一致性检查，运行验证由用户完成。

## 3. 三种队列的职责

### 3.1 Classic Queue

基础学习案例使用 Classic Queue：

- Direct Exchange：完全匹配 Routing Key。
- Topic Exchange：演示 `*` 和 `#`。
- Fanout Exchange：忽略 Routing Key，广播到两个独立队列。
- ACK 演示队列：分别触发 `basicAck`、`basicNack(requeue=true)` 和
  `basicReject(requeue=false)`。
- 顺序演示队列：设置 Single Active Consumer 和 prefetch=1，说明并发与严格顺序的取舍。

### 3.2 Quorum Queue

订单创建、缓存删除、订单统计、通知、订单超时检查、消费重试和最终死信使用 durable Quorum Queue。
本地只有一个 RabbitMQ 节点，因此能学习声明方式但没有真正多节点容灾；生产环境应使用至少三个节点，让副本多数派
确认后再向生产者返回 Publisher Confirm。

### 3.3 Stream

`pg.order.audit.stream` 绑定订单事件 Topic Exchange 的 `order.#`，保存订单事件审计日志。
另外提供原生 `RabbitStreamTemplate` 发布接口和 `StreamListenerContainer` 消费者，演示从第一个 offset 读取、
手动保存 offset 和应用重启后的继续消费。Stream 是审计与回放通道，不参与订单核心事务判断。

## 4. 消息拓扑

### 4.1 基础交换机

```text
pg.learning.direct.exchange
  └─ demo.direct.email -> pg.learning.direct.email.queue (classic)

pg.learning.topic.exchange
  ├─ demo.order.#   -> pg.learning.topic.order.queue (classic)
  └─ demo.*.paid    -> pg.learning.topic.paid.queue  (classic)

pg.learning.fanout.exchange
  ├─ pg.learning.fanout.queue.a (classic)
  └─ pg.learning.fanout.queue.b (classic)
```

### 4.2 可靠订单交换机

```text
pg.order.event.exchange (topic)
  ├─ order.created       -> pg.order.cache.queue (quorum)
  ├─ order.#             -> pg.order.statistics.queue (quorum)
  ├─ order.created       -> pg.order.notification.queue (quorum)
  ├─ order.timeout.check -> pg.order.timeout.queue (quorum)
  └─ order.#             -> pg.order.audit.stream (stream)
```

### 4.3 延迟、重试和死信

- 创建订单时同时生成 `ORDER_CREATED` 和 `ORDER_PAYMENT_TIMEOUT_CHECK` 两条 Outbox。
- 超时检查事件先发送到 `pg.order.delay.exchange`，进入消息 TTL 为 30 分钟的延迟队列。
- TTL 到期后，RabbitMQ 通过 DLX 将消息路由为 `order.timeout.check`。
- 每个业务消费者拥有独立重试队列。消费失败时先把消息发布到重试交换机，等待重试发布 Confirm 成功后，
  才 ACK 原消息；重试队列 TTL 到期后使用默认交换机回到原业务队列，避免重新广播给已经成功的消费者。
- 达到最大次数或消息版本无法识别时执行 `basicReject(requeue=false)`，由业务队列 DLX 进入对应最终死信队列。
- 只有“重试消息无法安全发布”这类短暂基础设施故障才使用 `basicNack(requeue=true)`，避免热循环。

## 5. 统一消息信封

```json
{
  "messageId": "UUID",
  "eventType": "ORDER_CREATED",
  "schemaVersion": 1,
  "aggregateId": "1001",
  "occurredAt": "2026-08-09T12:00:00",
  "payload": {}
}
```

- `messageId` 是发布确认关联 ID，也是消费幂等键。
- `eventType` 决定消费者业务。
- `schemaVersion` 用于兼容消息格式升级；无法识别的版本不能盲目消费。
- `aggregateId` 是订单 ID，便于日志、排障和分区。
- `payload` 使用 `JsonNode`，但 Controller 不直接暴露 AMQP `Message`。
- 发送时设置 `contentType=application/json`、`deliveryMode=PERSISTENT`、`messageId` 和版本 Header。

为避免 Spring AMQP 4.1 的 Jackson 3 消息转换器与项目现有 Jackson API 混用，业务信封由项目现有
`ObjectMapper` 显式序列化为 UTF-8 JSON 字节；Stream 示例也复用同一 JSON 格式。

## 6. Transactional Outbox

### 6.1 创建订单

新的 `RabbitOrderApplicationService.createOrder` 使用 `playgroundTransactionManager`：

1. 调用现有 `PgMyBatisService.createCompleteOrder` 创建订单、明细并扣库存。
2. 插入路由到 `pg.order.event.exchange/order.created` 的 Outbox。
3. 插入路由到 `pg.order.delay.exchange/order.timeout.delay` 的 Outbox。
4. 方法正常返回后，PostgreSQL 一次提交订单和两条 Outbox。

如果数据库提交成功而 RabbitMQ 不可用，订单和 Outbox 仍然存在，定时发布器稍后重试；不会出现“订单成功但消息
永远消失”。

### 6.2 Outbox 发布器

定时任务使用 PostgreSQL `FOR UPDATE SKIP LOCKED` 加 `UPDATE ... RETURNING` 原子认领一批事件：

- `PENDING/FAILED` 且到达 `next_retry_at` 的事件可以认领。
- 超过锁定超时时间的 `PROCESSING` 事件可以被恢复认领。
- Publisher Confirm 为 nack、超时或 Mandatory Return 时，记录错误和指数退避时间。
- Confirm 成功且没有 Return 时标记 `PUBLISHED`。
- Confirm 后进程崩溃、来不及标记数据库时会产生重复消息，因此消费者仍必须幂等。

## 7. 消费者可靠性

### 7.1 手动确认

- 业务成功、重复消息和已经过期的状态消息：`basicAck`。
- 重试发布成功：ACK 原消息，由重试队列稍后投递副本。
- 重试发布失败：`basicNack(requeue=true)`，不丢弃原消息。
- 超过重试次数或消息格式不可恢复：`basicReject(requeue=false)`，进入 DLQ。

### 7.2 幂等

`mq_consumed_message` 使用唯一键 `(consumer_name, message_id)`。统计和通知消费者先插入消费记录，
插入成功才执行核心写入，并放在同一个 PostgreSQL 事务中。缓存删除本身幂等，先删 Redis 再保存消费记录；
若保存记录失败，重复删除同一个缓存键仍然安全。

### 7.3 超时和乱序

订单超时消费者必须重新读取数据库，不能相信 30 分钟前的消息状态：

- `PENDING`：使用 `UPDATE ... WHERE status='PENDING'` 原子改为 `CANCELLED`，只有影响一行才恢复库存。
- `PAID/CANCELLED`：视为过期或重复消息，直接 ACK，不恢复库存。
- 成功取消后在同一个数据库事务中新增 `ORDER_CANCELLED` Outbox。

支付接口同样使用 `PENDING -> PAID` 条件更新，并新增 `ORDER_PAID` Outbox。因此无论支付和超时消息先后到达，
只有一个状态转换可以成功。

## 8. PostgreSQL 表

- `mq_outbox_event`：待发布事件、路由、状态、重试次数、锁定时间和错误。
- `mq_consumed_message`：每个消费者的幂等记录。
- `mq_order_statistics`：创建、支付、取消数量和累计创建金额。
- `mq_notification_log`：模拟通知落库结果。

建表脚本独立放在 `docs/rabbitmq-reliable-messaging-schema.sql`，全部使用 `CREATE TABLE IF NOT EXISTS`，
不删除现有业务数据；同时把完整建表定义补充到 `docs/schema-demo.sql`，便于重建 playground 数据库。

## 9. HTTP 接口

基础案例：

- `POST /api/playground/rabbitmq/demo/direct`
- `POST /api/playground/rabbitmq/demo/topic`
- `POST /api/playground/rabbitmq/demo/fanout`
- `POST /api/playground/rabbitmq/demo/mandatory-return`
- `POST /api/playground/rabbitmq/demo/ack`
- `POST /api/playground/rabbitmq/demo/retry`
- `POST /api/playground/rabbitmq/demo/ordering`
- `POST /api/playground/rabbitmq/stream/events`

可靠订单与观察接口：

- `POST /api/playground/rabbitmq/orders`
- `POST /api/playground/rabbitmq/orders/{orderId}/pay`
- `GET /api/playground/rabbitmq/outbox-events`
- `GET /api/playground/rabbitmq/consumed-messages`
- `GET /api/playground/rabbitmq/order-statistics`
- `GET /api/playground/rabbitmq/notification-logs`

接口继续返回项目现有 `Result<T>` 和 `PageResult<T>`。

## 10. 配置和本地环境

- AMQP：`localhost:5672`，账号 `root/123456`。
- Management UI：`localhost:15672`。
- 原生 Stream：`localhost:5552`。
- `publisher-confirm-type=correlated`、`publisher-returns=true`、`mandatory=true`。
- 普通监听器默认 `acknowledge-mode=manual`、prefetch=10、并发 `1-4`。
- 顺序案例单独使用 prefetch=1 和 Single Active Consumer。
- 本地 RabbitMQ 容器需要暴露 5552 并启用 `rabbitmq_stream` 插件。

## 11. 验收边界

静态检查需要确认：

- 三种队列均有明确声明和消费者或发布入口。
- Direct、Topic、Fanout 的 Binding 不混淆。
- 每个 `@RabbitListener` 都有清晰的手动确认路径。
- Outbox 与订单创建共用 `playgroundTransactionManager`。
- 重试发布成功前不会 ACK 原消息。
- `(consumer_name,message_id)` 唯一约束存在。
- 超时取消使用条件更新且只恢复一次库存。
- 文档包含调用顺序、管理界面观察点和典型失败场景。

按照用户约束，不创建测试文件，不运行 Maven、测试、应用启动或端到端消息验证；这些由用户完成。
