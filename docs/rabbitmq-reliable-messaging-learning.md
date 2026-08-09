# RabbitMQ 可靠消息学习指南

## 1. 这套案例解决什么问题

普通的消息发送只有一行代码：

```java
rabbitTemplate.convertAndSend(exchange, routingKey, message);
```

但是这一行不能独自回答下面的问题：

- 数据库提交成功后，应用还没发送消息就崩溃怎么办？
- 消息发送到一个不存在的 Routing Key，为什么生产者可能没有异常？
- Broker 收到消息以后宕机，消息有没有真正保存？
- 消费者执行一半崩溃，消息应该删除还是重新投递？
- 同一条消息收到两次，统计和通知会不会执行两次？
- 重试三次仍失败，消息放在哪里人工处理？
- 30 分钟后收到关闭订单消息，但订单已经支付怎么办？

本模块使用 Transactional Outbox、Publisher Confirm、Mandatory Return、手动 ACK、消费幂等、TTL、DLX、
Quorum Queue 和 Stream 分别处理这些问题。

## 2. 本地环境

RabbitMQ 容器：

```text
容器名：local-rabbitmq
AMQP：localhost:5672
管理界面：http://localhost:15672
Stream：localhost:5552
用户名：root
密码：123456
```

管理界面的 Queues 页面可以看到三种类型：

- `pg.learning.*`：Classic Queue。
- `pg.order.*.queue`：Quorum Queue。
- `pg.order.audit.stream`：Stream。

当前是单节点 RabbitMQ。Quorum Queue 可以正常学习声明和使用，但没有真正的多数副本高可用；生产环境通常至少使用
三个 RabbitMQ 节点。

## 3. 创建 PostgreSQL 学习表

业务库是 `demo`。运行下面的独立脚本：

```bash
psql -h localhost -p 5432 -U root -d demo \
  -f docs/rabbitmq-reliable-messaging-schema.sql
```

脚本只使用 `CREATE TABLE IF NOT EXISTS`，不会删除原有 users、orders、products 和 order_products。

新增表：

```text
mq_outbox_event       待发布消息与发布结果
mq_consumed_message   消费者幂等记录
mq_order_statistics   订单事件统计
mq_notification_log   模拟通知记录
```

## 4. 建议学习顺序

### 第1步：Direct Exchange

```http
POST /api/playground/rabbitmq/demo/direct
Content-Type: application/json

{
  "routingKey": "demo.direct.email",
  "message": "发送一封邮件"
}
```

只有 Routing Key 完全等于 `demo.direct.email` 才进入 `pg.learning.direct.email.queue`。

### 第2步：Topic Exchange

```http
POST /api/playground/rabbitmq/demo/topic
Content-Type: application/json

{
  "routingKey": "demo.order.paid",
  "message": "订单已支付"
}
```

`demo.order.paid` 同时满足：

```text
demo.order.#
demo.*.paid
```

所以一条消息会分别复制到两个队列。这里不是两个消费者争抢同一条消息，而是两个队列各有一份。

### 第3步：Fanout Exchange

```http
POST /api/playground/rabbitmq/demo/fanout
Content-Type: application/json

{
  "message": "系统广播"
}
```

Fanout 忽略 Routing Key，queue.a 和 queue.b 都会收到。

### 第4步：Mandatory Return

```http
POST /api/playground/rabbitmq/demo/mandatory-return
Content-Type: application/json

{
  "message": "这条消息故意无法路由"
}
```

接口预期返回 `success=false`。重要区别：

```text
Publisher Confirm ACK：Broker 接管了这次发布
Mandatory Return：消息没有路由到任何队列
```

因此收到 ACK 不代表一定存在目标队列，两种结果必须一起检查。

### 第5步：ACK、NACK 和 Reject

正常确认：

```json
{
  "message": "正常处理",
  "action": "ACK",
  "failTimes": 0
}
```

调用：

```http
POST /api/playground/rabbitmq/demo/ack
```

只重新入队一次：

```json
{
  "message": "第一次NACK，第二次ACK",
  "action": "NACK_REQUEUE_ONCE",
  "failTimes": 0
}
```

直接进入死信队列：

```json
{
  "message": "不可恢复消息",
  "action": "REJECT_TO_DEAD",
  "failTimes": 0
}
```

对应关系：

```text
basicAck(tag, false)
  当前消息处理完成，RabbitMQ 可以删除它

basicNack(tag, false, true)
  当前消息失败，重新放回原队列

basicReject(tag, false)
  当前消息失败且不回原队列；配置了 DLX 时进入死信流程
```

### 第6步：TTL 重试

前两次失败，第三次成功：

```http
POST /api/playground/rabbitmq/demo/retry
Content-Type: application/json

{
  "message": "模拟短暂网络错误",
  "failTimes": 2
}
```

执行过程：

```text
主队列消费失败
  ↓ 发布新副本并等待 Confirm
重试队列等待 5 秒
  ↓ TTL 到期，通过默认交换机
回到原主队列
```

只有重试副本收到 Confirm 后，消费者才 ACK 原消息。如果重试副本发布失败，代码会对原消息执行
`basicNack(requeue=true)`，优先保证不丢失。

默认最大重试次数是 3。`failTimes=5` 时，消息最终进入 `pg.learning.ack.dead.queue`。

### 第7步：顺序消息

```http
POST /api/playground/rabbitmq/demo/ordering
Content-Type: application/json

{
  "businessKey": "order-1001",
  "count": 10
}
```

`pg.learning.ordering.queue` 使用：

```text
x-single-active-consumer=true
prefetch=1
消费者并发数=1
```

这适合学习严格顺序，但吞吐量较低。普通队列增加消费者并发后，RabbitMQ 仍按入队顺序分发，实际业务完成顺序却可能
因为每个线程耗时不同而变化；NACK 重投也可能改变观察到的顺序。

## 5. Transactional Outbox 订单案例

### 5.1 创建订单

确保 users 和 products 中存在对应 ID，然后调用：

```http
POST /api/playground/rabbitmq/orders
Content-Type: application/json

{
  "userId": 1,
  "orderNo": "MQ202608090001",
  "items": [
    {"productId": 1, "quantity": 1},
    {"productId": 3, "quantity": 2}
  ]
}
```

一个 PostgreSQL 事务中发生：

```text
插入 orders
插入 order_products
条件扣减 products.stock
插入 ORDER_CREATED Outbox
插入 ORDER_PAYMENT_TIMEOUT_CHECK Outbox
统一 COMMIT
```

接口返回两个 messageId。立即查询：

```http
GET /api/playground/rabbitmq/outbox-events
GET /api/playground/rabbitmq/outbox-events/{messageId}
```

Outbox 状态变化：

```text
PENDING → PROCESSING → PUBLISHED
                   ↘ FAILED → 等待退避 → PROCESSING
                   ↘ DEAD（达到最大发布次数）
```

### 5.2 为什么不用“数据库提交后直接发消息”

错误时间线：

```text
数据库 COMMIT 成功
应用进程崩溃
rabbitTemplate.send 尚未执行
```

订单已经存在，但消息永远不会出现。Outbox 把“需要发送消息”本身保存进订单事务，应用恢复后定时器还能继续投递。

### 5.3 为什么仍可能重复

另一条时间线：

```text
RabbitMQ 已 Confirm
应用在 UPDATE mq_outbox_event 之前崩溃
PROCESSING 锁超时
定时器重新发送
```

所以 Outbox 提供至少一次投递，不是魔法般的恰好一次。缓存、统计和通知分别使用：

```sql
UNIQUE (consumer_name, message_id)
```

重复消息再次插入时影响 0 行，消费者将其视为已经成功并 ACK。

### 5.4 支付与超时竞争

支付：

```http
POST /api/playground/rabbitmq/orders/{orderId}/pay
```

支付和超时取消分别执行：

```sql
UPDATE orders SET status = 'PAID'
WHERE id = ? AND status = 'PENDING';

UPDATE orders SET status = 'CANCELLED'
WHERE id = ? AND status = 'PENDING';
```

两者并发时只能有一条 SQL 影响一行：

- 支付先成功：超时消息重新查询到 PAID，作为过期消息 ACK，不恢复库存。
- 超时先成功：支付接口发现状态不再是 PENDING，拒绝支付。
- 同一超时消息重复：消费唯一键阻止第二次恢复库存。

学习时不必真的等 30 分钟，可以启动应用前临时设置：

```bash
RABBITMQ_ORDER_TIMEOUT_MILLIS=10000
```

这会把延迟缩短为 10 秒。已经创建的队列参数不能直接修改；如管理界面存在旧参数队列，应先删除该学习队列再重启应用
重新声明。

## 6. Stream 回放案例

原生发布：

```http
POST /api/playground/rabbitmq/stream/events
Content-Type: application/json

{
  "eventType": "LEARNING_NOTE_CREATED",
  "aggregateId": "note-1",
  "payload": {
    "title": "学习RabbitMQ Stream",
    "chapter": 1
  }
}
```

Stream 与普通队列的核心区别：

```text
普通队列：ACK 后消息通常从队列移除
Stream：消息按保留策略保存，消费者只推进自己的 offset
```

`pg-order-audit-reader-v1` 第一次从 `OffsetSpecification.first()` 开始。处理成功后执行
`context.storeOffset()`；应用重启时可以继续读取上一次保存位置之后的消息。

订单 Topic Exchange 的 `order.#` 也绑定了 `pg.order.audit.stream`，因此 ORDER_CREATED、ORDER_PAID、
ORDER_CANCELLED 和超时检查都可以作为审计事件保留。

## 7. 观察接口与 SQL

```http
GET /api/playground/rabbitmq/consumed-messages
GET /api/playground/rabbitmq/order-statistics
GET /api/playground/rabbitmq/notification-logs
```

也可以直接查询：

```sql
SELECT id, event_type, status, retry_count, last_error, created_at, published_at
FROM mq_outbox_event
ORDER BY created_at DESC;

SELECT consumer_name, message_id, event_type, consumed_at
FROM mq_consumed_message
ORDER BY consumed_at DESC;

SELECT * FROM mq_order_statistics;
SELECT * FROM mq_notification_log ORDER BY created_at DESC;
```

## 8. 五类故障如何处理

| 故障 | 处理方式 |
|---|---|
| 数据库成功、RabbitMQ 发送失败 | Outbox 保持 FAILED，定时器退避重试 |
| 消息无法路由 | mandatory Return，Outbox 不标记 PUBLISHED |
| 消费者业务失败 | 独立 TTL 重试队列，重试发布 Confirm 后 ACK 原消息 |
| 同一消息收到两次 | `(consumer_name,message_id)` 唯一键幂等 |
| 多次重试仍失败 | `basicReject(requeue=false)` 后进入最终 DLQ |
| 支付后又收到超时消息 | 重新查询状态并使用条件更新，作为过期消息 ACK |
| Confirm 后进程崩溃 | 允许重复投递，由消费者幂等吸收 |

## 9. 生产环境还需要什么

本模块为了突出核心原理，没有加入完整生产运维设施。真实系统还应考虑：

- 三节点以上 RabbitMQ 集群与 Quorum 副本布局。
- 对 Quorum Queue 配置 `at-least-once` dead-letter strategy 和合适的长度限制。
- DLQ 告警、人工重放、Outbox DEAD 告警和积压监控。
- 真实短信服务的供应商幂等键或第二层通知 Outbox。
- 消息体大小限制、敏感字段脱敏、Trace ID 和 Micrometer 指标。
- 数据库表归档，避免 Outbox 与消费记录无限增长。

官方参考：

- [RabbitMQ Reliability Guide](https://www.rabbitmq.com/docs/reliability)
- [Consumer Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms)
- [Quorum Queues](https://www.rabbitmq.com/docs/quorum-queues)
- [Dead Letter Exchanges](https://www.rabbitmq.com/docs/dlx)
- [Spring AMQP Stream](https://docs.spring.io/spring-amqp/reference/stream.html)
