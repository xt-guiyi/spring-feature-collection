# RocketMQ 5.x 可靠消息学习指南

这套案例的重点不是“成功发送一条字符串”，而是理解业务系统真正会遇到的五类问题：

1. 数据库提交成功，但消息没发出去；
2. Broker 已接收消息，但生产者没有拿到明确结果；
3. 消费者处理失败，需要重试或进入死信队列；
4. 同一条消息被投递多次，业务副作用不能重复执行；
5. 同一业务对象的消息乱序到达，旧消息不能覆盖新状态。

项目提供两套独立的“数据库 + 消息”可靠写入入口：

- Transactional Outbox：先在同一个 PostgreSQL 事务中写业务数据和本地消息表，再由定时任务可靠发布；
- RocketMQ 事务消息：先持久化 PREPARED 回查锚点并发送半消息，再执行本地事务，
  最后提交或回滚半消息；半消息状态不明时由 Broker 回查，未到达 Broker 的记录由主动清理收口。

同一笔订单只选一种方案，不要同时使用两套机制，否则会产生两条语义相同的事件。

## 1. 先认识五个运行组件

| 组件         | 本地地址                     | 主要职责                                   | 不负责什么                     |
| ---------- | ------------------------ | -------------------------------------- | ------------------------- |
| NameServer | `localhost:9876`         | 保存 Topic 到 Broker 的路由信息                | 不保存业务消息                   |
| Broker     | `10909/10911/10912`      | 持久化消息、维护消费进度、重试和死信                     | 不提供本项目 Java 5.x gRPC 接入地址 |
| Proxy      | `localhost:18081`        | 接收 RocketMQ 5.x gRPC 客户端请求并访问 Broker   | 不替代 Broker 持久化消息          |
| Dashboard  | `http://localhost:18082` | 查看 Topic、消息、ConsumerGroup 和消费进度        | 不参与消息可靠传输                 |
| Init       | 一次性容器                    | 等 Broker 注册后创建四种 Topic 和 ConsumerGroup | 完成初始化后不常驻                 |

Java 配置中的 `ROCKETMQ_ENDPOINTS` 应填写 Proxy 的 `localhost:18081`，不是 NameServer 的 `9876`。

当前 Compose 是单 NameServer、单 Broker 的学习环境，没有副本切换能力，不能照搬到生产环境。生产环境还需评估多副本、磁盘、刷盘策略、监控、告警、鉴权和容量规划。

## 2. 启动顺序

### 2.1 启动 RocketMQ

先进入本项目同级的 Docker 目录执行（本文后续命令继续以这个目录为当前位置）：

```bash
cd ../docker
docker compose up -d rocketmq-nameserver rocketmq-broker rocketmq-proxy rocketmq-dashboard
docker compose run --rm rocketmq-init
```

初始化任务创建以下 Topic：

| Topic                     | 类型          | 用途                    |
| ------------------------- | ----------- | --------------------- |
| `pg_learning_normal`      | NORMAL      | 普通、异步、Tag、重试、订单业务事件   |
| `pg_learning_fifo`        | FIFO        | 按 MessageGroup 保证局部顺序 |
| `pg_learning_delay`       | DELAY       | 任意延迟和订单超时检查           |
| `pg_learning_transaction` | TRANSACTION | RocketMQ 事务半消息        |

本 Compose 固定使用 RocketMQ 5.5.0，该版本 Proxy 默认开启 Topic 消息类型校验。向 FIFO Topic 发送普通消息、
向 NORMAL Topic 发送事务消息会被拒绝，因此本案例明确拆成四个 Topic。这个校验发生在 Proxy，不能通过给
Broker 随意添加一个同名参数来开启或关闭。

### 2.2 初始化 PostgreSQL

Compose 中原有 PostgreSQL 服务的 `POSTGRES_DB` 是 `test_db`，但本项目的数据源连接 `demo`。
官方 PostgreSQL 镜像只会在首次创建数据卷时自动创建一个 `POSTGRES_DB`，所以不能假设已有卷里一定存在
`demo`。先执行一次幂等初始化任务：数据库已经存在时它只会跳过，不会删除或覆盖数据。

```bash
docker compose up -d postgres
docker compose run --rm postgres-demo-init
```

然后仍在 `backend/docker` 目录，把项目中的 SQL 通过标准输入交给 PostgreSQL 容器。这里显式使用
`../spring-feature-collection/...`，避免在 Docker 目录直接写 `docs/...` 导致找不到文件：

```bash
# 首次学习可先创建 users、products、orders 等基础表和示例数据。
# 注意：schema-demo.sql 是重建型学习脚本，会 DROP 并重建相关表；已有重要数据时不要执行这一行。
docker exec -i local-postgres psql -U root -d demo \
  < ../spring-feature-collection/docs/schema-demo.sql

# RocketMQ 脚本只重建 5 张 mq_* 学习表，不会删除 users、products、orders 等基础业务表。
# 它不是增量迁移脚本，重复执行会清空已有的 RocketMQ 学习记录。
docker exec -i local-postgres psql -U root -d demo \
  < ../spring-feature-collection/docs/rocketmq-reliable-messaging-schema.sql
```

### 2.3 启动应用前检查

- PostgreSQL `demo` 库已有 `users/products/orders/order_products`；
- 至少有一个状态为 `ACTIVE` 的用户；
- 商品有足够库存；
- Proxy 地址是 `localhost:18081`；
- Init 容器已成功创建 Topic 和 ConsumerGroup。

本次代码交付不代替你启动环境，也不会自动执行 SQL。

依赖兼容性边界：本案例使用 Apache RocketMQ 官方
`rocketmq-v5-client-spring-boot-starter:2.3.6`，其源码中的 Spring Boot/Spring 构建基线是
`2.7.18/5.3.27`，而当前项目是 Spring Boot `4.1.0`。本次只核对了所调用的官方 API 和自动配置入口，
按约定没有执行 Maven 构建或启动，因此不能声称两者已经通过运行时兼容验证。若启动时出现确定的 Spring 二进制
兼容问题，应优先替换集中在 `RocketMessagePublisher`、监听器和配置层的适配代码，不需要推翻 Outbox、幂等和状态机。

## 3. 六个核心概念

### 3.1 Topic

Topic 是消息的大分类，例如订单事件统一进入 `pg_learning_normal`。Topic 不应该细化成“每个订单一个 Topic”。

### 3.2 MessageQueue

一个 Topic 可以有多个 MessageQueue，它们是 Broker 并行存储和消费的分片。队列越多通常并行度越高，但顺序范围也被限制在选定的队列/MessageGroup 内。

### 3.3 Tag

Tag 是 Topic 内的二级分类。本项目使用：

- `DEMO`
- `RETRY_DEMO`
- `ORDER_CREATED`
- `ORDER_PAID`
- `ORDER_CANCELLED`
- `ORDER_PAYMENT_TIMEOUT_CHECK`

消费者可通过 Tag 表达式只订阅自己关心的事件，避免收到后再由 Java 大量丢弃。但 Tag 过滤只证明
Broker 路由命中，不能证明 JSON 信封的 `eventType` 与 Tag 相符。`RocketConsumerSupport` 在调用业务 handler 前
还会校验以下显式契约：

| 监听器用途              | 实际 Tag                        | 允许的 envelope.eventType        |
| ------------------ | ----------------------------- | ----------------------------- |
| 普通/FIFO/延迟/多组 Demo | `DEMO`                        | `DEMO_MESSAGE`                |
| 重试 Demo            | `RETRY_DEMO`                  | `DEMO_MESSAGE`                |
| 普通订单事件             | `ORDER_CREATED`               | `ORDER_CREATED`               |
| 普通订单事件             | `ORDER_PAID`                  | `ORDER_PAID`                  |
| 普通订单事件             | `ORDER_CANCELLED`             | `ORDER_CANCELLED`             |
| 订单超时检查             | `ORDER_PAYMENT_TIMEOUT_CHECK` | `ORDER_PAYMENT_TIMEOUT_CHECK` |
| 事务半消息              | `ORDER_CREATED`               | `TRANSACTION_ORDER_CREATED`   |

事务 Topic 虽然复用 `ORDER_CREATED` Tag，但它的信封事件是独立的
`TRANSACTION_ORDER_CREATED`，不能与普通 Topic 的创建事件混用。缺失 Tag、Tag 不在允许表中或
eventType 不匹配时，公共模板抛出协议异常，在任何通知、统计、缓存或取消订单副作用前返回
`FAILURE`；Broker 有限重试后转入 DLQ 保留错误消息，不会将它当成消费成功静默 ACK。

### 3.4 Key

Key 用于按业务标识检索消息。本项目会使用稳定的订单号、businessKey 或业务消息 ID；同一个 Outbox 重试时 Key
必须保持不变。Key 不是数据库唯一约束，也不自动提供消费者幂等，幂等仍由 `mq_consumed_message` 的唯一键保证。

### 3.5 ConsumerGroup

- 同一个 ConsumerGroup 内的多个实例共同分担消息；
- 不同 ConsumerGroup 各自维护消费进度，因此每组都能收到一份消息；
- 修改 ConsumerGroup 名称相当于创建新的消费身份和新的消费进度，不能随意改名。

订单创建事件会被缓存、统计、通知三个不同组各消费一次。这就是不同 ConsumerGroup 各自维护消费进度形成的发布订阅效果。

### 3.6 MessageGroup

FIFO 消息使用 MessageGroup 选择有序范围。例如同一订单号作为 MessageGroup，可保证该订单的 `创建 -> 支付 -> 发货` 顺序；不同订单可以并行处理。

RocketMQ 保证的是同一 MessageGroup 内的顺序，不是整个 Topic 的全局顺序。

## 4. 消息协议为什么还要有 envelope

消息体统一使用以下结构：

```json
{
  "messageId": "业务消息UUID",
  "eventType": "ORDER_CREATED",
  "schemaVersion": 1,
  "aggregateId": "10001",
  "occurredAt": "2026-08-12T10:00:00",
  "payload": {}
}
```

- `messageId`：消费者幂等键，不能在每次重试时重新生成；
- `eventType`：表示发生了什么，而不是让消费者执行任意命令；
- `schemaVersion`：消息结构升级时选择正确解析逻辑；
- `aggregateId`：订单事件使用 orderId，事务消息使用 transactionId 等业务聚合标识；
- `occurredAt`：判断延迟和乱序的业务时间；
- `payload`：当前版本需要的业务快照。

Broker 的 MessageId 与业务 `messageId` 含义不同：前者由 Broker/客户端生成，用于追踪投递；后者由业务生成，用于跨重试、Outbox 和消费幂等。

## 5. 基础消息案例

统一前缀：

```text
/api/playground/rocketmq/demo
```

### 5.1 同步普通消息

```http
POST /api/playground/rocketmq/demo/normal
Content-Type: application/json

{
  "text": "第一条 RocketMQ 普通消息",
  "tag": "DEMO"
}
```

HTTP 返回业务消息 ID、Broker MessageId、Topic、Tag 和 Key。同步成功表示发送调用拿到了 Broker 回执，但消费者业务仍可能尚未执行。

观察：

- 应用日志中的发送回执；
- `NormalTagConsumer` 消费日志；
- Dashboard 的 Topic 消息查询。

### 5.2 异步普通消息

```http
POST /api/playground/rocketmq/demo/async
Content-Type: application/json

{
  "text": "异步发送不会等待 Broker 回执后再返回 HTTP",
  "tag": "DEMO"
}
```

HTTP 中的 `accepted=true` 只表示应用已接受异步任务，不等于 Broker 已接受消息。最终结果要看异步回调日志和 Dashboard。

### 5.3 Tag 与不同消费组

把消息发送到 NORMAL Topic 后，观察不同 ConsumerGroup：

- Tag 不匹配的消费者不会收到；
- 审计组和通知组使用不同 ConsumerGroup，因此都能收到同一条消息；
- 同组启动两个应用实例时，消息只由其中一个实例处理。

### 5.4 FIFO 顺序消息

```http
POST /api/playground/rocketmq/demo/fifo
Content-Type: application/json

{
  "businessKey": "ORDER-DEMO-001",
  "count": 5
}
```

服务会用同一个 `businessKey` 作为 MessageGroup，依次发送序号 `1..5`。再用另一个 businessKey 调用一次，可以观察两个组之间并行、每个组内部有序。

### 5.5 延迟消息

```http
POST /api/playground/rocketmq/demo/delay
Content-Type: application/json

{
  "text": "10秒后才能消费",
  "delaySeconds": 10
}
```

消费者日志会记录期望投递时间、实际接收时间和偏差。延迟投递不是精准定时器，Broker 调度、网络和消费者负载都会带来偏差。

### 5.6 消费重试和死信

```http
POST /api/playground/rocketmq/demo/retry
Content-Type: application/json

{
  "text": "前两次故意失败，第三次成功",
  "failTimes": 2
}
```

消费者读取 `MessageView.getDeliveryAttempt()`：在指定次数内返回 `FAILURE`，之后返回 `SUCCESS`。Java 代码不会自己复制一条“重试消息”，重投由 Broker 负责。

若 `failTimes` 大于 ConsumerGroup 配置的最大重试次数，消息最终进入：

```text
%DLQ%pg_learning_retry_demo_group_v1
```

死信不是“自动处理完成”。生产环境需要告警、人工诊断、修复原因和受控重放，不能无限自动重放毒消息。

### 5.7 应用层批量发送

```http
POST /api/playground/rocketmq/demo/batch
Content-Type: application/json

{
  "items": [
    {"text": "batch-1", "tag": "DEMO"},
    {"text": "batch-2", "tag": "DEMO"}
  ]
}
```

本接口只是把一次 HTTP 请求拆成多条独立消息并分别返回结果，不宣称底层一定合并成一个 Broker 批量传输帧。部分成功时要逐条查看结果。

## 6. Transactional Outbox 订单案例

### 6.1 创建订单

```http
POST /api/playground/rocketmq/orders/outbox
Content-Type: application/json

{
  "userId": 1,
  "orderNo": "RMQ-OUTBOX-001",
  "items": [
    {"productId": 1, "quantity": 1},
    {"productId": 2, "quantity": 2}
  ]
}
```

同一个 PostgreSQL 本地事务完成：

```text
创建订单 + 扣库存
        + 写 ORDER_CREATED Outbox
        + 写 ORDER_PAYMENT_TIMEOUT_CHECK Outbox
```

这里没有 PostgreSQL 与 RocketMQ 的分布式事务。HTTP 返回时消息可能还在 `PENDING` 状态，定时发布器随后使用 `FOR UPDATE SKIP LOCKED` 抢占任务并发布。

### 6.2 应观察的数据库状态

```sql
SELECT id, event_type, topic_name, message_tag, status,
       retry_count, next_retry_at, deliver_at, last_error
FROM mq_outbox_event
ORDER BY created_at DESC;
```

典型状态：

```text
PENDING -> PROCESSING -> PUBLISHED
                    -> FAILED -> PROCESSING -> ...
                    -> DEAD
```

发布器崩溃窗口：Broker 已收消息，但数据库还没来得及把 Outbox 标成 `PUBLISHED`。恢复后会再次发送，所以该方案提供的是“至少一次”，不是“恰好一次”。

### 6.3 支付订单

```http
POST /api/playground/rocketmq/orders/{orderId}/pay
```

SQL 使用条件更新：

```sql
UPDATE orders
SET status = 'PAID'
WHERE id = ? AND status = 'PENDING';
```

只有受影响行数为 1 的事务才能写 `ORDER_PAID` Outbox。受影响行数为 0 可能表示订单不存在，也可能表示另一事务已支付或已取消，不能继续无条件覆盖状态。

### 6.4 订单超时为什么发送“检查”而不是“取消命令”

30 分钟延迟消息到达时，订单可能已经支付。消费者必须重新读取当前状态，并执行 `PENDING -> CANCELLED` 条件更新；只有更新成功才按 productId 固定顺序恢复库存并发布 `ORDER_CANCELLED`。

固定 productId 顺序可降低两个事务以相反顺序锁定多件商品而发生死锁的概率。

## 7. RocketMQ 事务消息订单案例

```http
POST /api/playground/rocketmq/orders/transaction-message
Content-Type: application/json

{
  "userId": 1,
  "orderNo": "RMQ-TX-001",
  "items": [
    {"productId": 1, "quantity": 1}
  ]
}
```

完整步骤：

```text
1. PostgreSQL 独立事务写 PREPARED 回查记录
2. 向 TRANSACTION Topic 发送半消息（消费者暂时不可见）
3. 独立 Spring Bean 执行创建订单 + 扣库存
4. 同一数据库事务把回查记录改为 COMMITTED 并保存 orderId
5. 调用 RocketMQ transaction.commit()，半消息才对消费者可见
```

### 7.1 为什么只有 Broker 回查还不够

`PREPARED` 必须先于半消息持久化，否则 Broker 回查时没有稳定的数据库事实。但这个顺序会产生一个必须显式处理的窄窗口：

```text
PostgreSQL 提交 PREPARED ── 进程/主机崩溃 ──X──> Broker 收到半消息
```

此时 Broker 从未见过这条半消息，所以它不可能主动发起回查。`PreparedTransactionCleanupScheduler`
每 30 秒分批查找超过 `transaction-prepared-timeout-seconds` 的候选，每轮批量由
`transaction-cleanup-batch-size` 限制。它的处理步骤是：

1. 短事务读取“过期、仍为 `PREPARED`、且没有 `orderId`”的候选，不长时间锁住一批行；
2. 对每条候选在独立短事务执行 `PREPARED -> ROLLED_BACK` 条件更新；
3. 更新 1 行才表示清理器抢占终态；若根本没有半消息，数据库孤儿至此已收口；
4. 更新 0 行必须重读；`COMMITTED` 说明本地事务先赢，`ROLLED_BACK` 说明 checker 或另一实例先赢，其他情况不猜测并留到下轮；
5. 如果 Broker 其实已收到半消息，清理器不保存内存 `Transaction` 句柄，之后的 Broker checker 会读到 `ROLLED_BACK` 并回滚它。

这与 checker 和本地事务使用同一个数据库裁决点：清理先赢时，本地事务的
`PREPARED -> COMMITTED` 更新为 0，订单和库存整体回滚；本地事务先提交时，清理条件更新为 0 并重读 `COMMITTED`。

`transaction-prepared-timeout-seconds` 是安全性边界，必须大于正常本地订单事务的最坏执行时间，
并要把数据库行锁等待等尾延计入。该值过小时，checker/清理器会按规则先抢占 `ROLLED_BACK`，
后到的慢订单事务在执行 `PREPARED -> COMMITTED` 时影响 0 行，其订单和库存修改会整体回滚。

### 7.2 回滚后如何安全重试同一 orderNo

如果 `business_key` 使用全局 `UNIQUE`，即使孤儿已收口为 `ROLLED_BACK`，同一 `orderNo`
仍会永久插入失败。本案例使用 PostgreSQL 部分唯一索引：

```sql
CREATE UNIQUE INDEX uk_mq_transaction_active_business_key
    ON mq_transaction_record (business_key)
    WHERE status IN ('PREPARED', 'COMMITTED');
```

- `PREPARED` 仍在执行：同 orderNo 新请求被拒绝；
- `COMMITTED` 已形成订单事实：同 orderNo 永久被拒绝；
- `ROLLED_BACK` 已终结且没有订单事实：旧记录保留审计，但不占用索引，可以使用新 `transactionId` 重试；
- 多个重试并发：部分唯一索引只允许一条新 `PREPARED` 成功。

状态更新和索引成员变化在同一 PostgreSQL 事务中原子提交，不需要先删记录或手工释放键。
本学习项目的独立建表脚本直接创建上面的部分唯一索引，不包含旧结构迁移逻辑；重复执行脚本会重建
5 张 `mq_*` 表并清空其中的学习记录。

### 7.3 其他失败分支与 Broker 回查

失败分支：

- 本地事务明确失败：回查记录标为 `ROLLED_BACK`，调用 `rollback()`；
- 本地事务方法抛异常但数据库提交结果不明确：先尝试 `PREPARED -> ROLLED_BACK` 条件更新；更新 0 行后必须重读，
  若已是 `COMMITTED` 就提交半消息，若是 `ROLLED_BACK` 才回滚半消息，仍不明确则不猜测并等待回查；
- 数据库已提交，但向 Broker 发送 commit 的网络调用失败：不能回滚已经提交的订单，保留 `COMMITTED`，等待 Broker 回查；
- Broker 回查时只依赖 `mq_transaction_record` 持久状态裁决，不依赖应用内存 Map，应用重启后仍可判断。

回查映射：

| 持久状态                   | 回查结果                                            |
| ---------------------- | ----------------------------------------------- |
| `COMMITTED`            | `COMMIT`                                        |
| `ROLLED_BACK`          | `ROLLBACK`                                      |
| 尚未超时的 `PREPARED`       | `UNKNOWN`，稍后再查                                  |
| 已超时且没有订单事实的 `PREPARED` | 先用数据库条件更新抢占 `ROLLED_BACK` 终态；抢占成功才返回 `ROLLBACK` |

过期判断本身不能直接向 Broker 返回 `ROLLBACK`。回查必须先在独立短事务中执行
`PREPARED -> ROLLED_BACK` 条件更新，把数据库状态作为与本地订单事务竞争的唯一裁决点：

- 回查先更新成功：本地事务随后执行 `PREPARED -> COMMITTED` 会影响 0 行，并连同订单和库存修改一起回滚；
- 本地事务先提交：回查的条件更新影响 0 行，必须重新读取记录，并根据 `COMMITTED` 返回 `COMMIT`；
- 条件更新失败后仍未读到明确终态：返回 `UNKNOWN`，不能依据更新前的旧快照猜测回滚。

## 8. Outbox 与事务消息如何选择

| 对比项      | Transactional Outbox | RocketMQ 事务消息          |
| -------- | -------------------- | ---------------------- |
| 绑定 MQ 厂商 | 低，本地消息表是通用模式         | 高，依赖 RocketMQ 半消息和回查协议 |
| 主事务路径    | 只写数据库，MQ 发布异步完成      | 主流程需要与 Broker 交互       |
| 消息延迟     | 受轮询周期影响              | commit 后较快可见           |
| 运维重点     | Outbox 堆积、锁恢复、发布重试   | 半消息回查、事务记录、UNKNOWN 时长  |
| 迁移其他 MQ  | 发布适配器可替换             | 需要重做事务协调机制             |
| 共同要求     | 消费幂等、状态条件更新、监控、补偿    | 消费幂等、状态条件更新、监控、补偿      |

二者解决的核心问题相同：避免“数据库成功、消息丢失”这种双写不一致；解决步骤和依赖边界不同。

## 9. 消费者幂等

RocketMQ 的可靠消费通常是至少一次投递，因此重复消息是正常边界，不是偶发异常。

消费者先尝试插入：

```sql
INSERT INTO mq_consumed_message (...)
VALUES (...)
ON CONFLICT (consumer_name, message_id) DO NOTHING;
```

唯一键是 `(consumer_name, message_id)`：

- 同一个消费组重复收到同一业务消息时，只有第一次插入成功；
- 不同消费组可以各执行一次自己的业务副作用；
- 幂等记录和 PostgreSQL 业务变更必须处于同一本地事务，否则可能“记录已消费但业务没完成”。

缓存删除天然适合重复执行，但发送通知、累计统计、恢复库存都必须有幂等保护。

## 10. 运维观察接口

统一前缀：

```text
/api/playground/rocketmq/operations
```

可分页观察：

- Outbox 状态和失败原因；
- 事务消息持久回查记录；
- 每个消费者的幂等记录；
- 模拟通知发送日志；
- 订单事件统计。

当统计表还没有记录时，接口返回字段为 0 的稳定对象，而不是让前端处理 `null`。

## 11. 推荐故障演练

### 11.1 数据库成功、消息暂时发不出去

停止 Proxy，调用 Outbox 创建订单，确认订单和 `PENDING/FAILED` Outbox 同时存在；恢复 Proxy 后观察定时任务重试并标为 `PUBLISHED`。

### 11.2 Broker 已收消息、Outbox 未标记成功

这是非常窄的崩溃窗口，最终效果是消息可能重复。重点不是强行模拟每个纳秒窗口，而是检查消费者唯一约束能否让重复副作用变成无操作。

### 11.3 PREPARED 已持久化、半消息尚未发送

在 `RocketTransactionMessageService` 写入 PREPARED 后、调用 `beginTransaction` 前模拟进程中断，
并观察 `PreparedTransactionCleanupScheduler`。记录超过保护窗口后应通过条件更新进入
`ROLLED_BACK`，然后同一 `orderNo` 才能用新 `transactionId` 受控重试。

### 11.4 消费持续失败

发送 `failTimes` 大于最大重试次数的消息，在 Dashboard 查看重试次数和 `%DLQ%pg_learning_retry_demo_group_v1`。

### 11.5 重复支付或超时与支付并发

对同一订单并发调用支付，并等待超时消费者执行。最终只能有一个 `PENDING -> PAID/CANCELLED` 条件更新成功，库存恢复只能发生一次。

### 11.6 顺序和过期消息

对同一 businessKey 发送多条 FIFO 消息观察局部顺序；对业务状态事件仍应携带版本或发生时间，在消费者侧拒绝旧版本覆盖新状态。FIFO 能减少乱序，但不能替代业务状态机。

## 12. 查看顺序建议

第一次学习按以下顺序：

1. `RocketMqNames`：先看 Topic、Tag、ConsumerGroup；
2. `RocketMessageEnvelope`：理解业务消息协议；
3. `RocketMessagePublisher`：看四类消息如何调用官方客户端；
4. 基础 Demo Controller 和 Consumer：理解发送与消费；
5. `OutboxEventService` 与 `OutboxPublishScheduler`：理解可靠补发；
6. `RocketOrderConsumerService`：理解幂等、条件更新和库存恢复；
7. `RocketTransactionMessageService` 与事务回查器：理解半消息；
8. `PreparedTransactionCleanupScheduler`：理解 Broker 没有半消息时的孤儿收口与并发裁决；
9. Mapper XML 和 SQL：把 Java 流程落回数据库原子操作；
10. Dashboard 与运维接口：建立可观测性视角。

不要从 Controller 一路机械跟方法调用。每学一个案例，都先回答三句话：

- 业务事实先保存在哪里？
- 失败后由谁重试，重试依据保存在哪里？
- 重复执行时靠什么保证不会产生第二次业务副作用？
