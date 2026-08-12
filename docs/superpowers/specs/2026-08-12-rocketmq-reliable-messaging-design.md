# RocketMQ 5.x 可靠消息学习模块设计

## 1. 目标

实现独立的 RocketMQ 5.x 学习模块，统一接口前缀为：

```text
/api/playground/rocketmq/**
```

模块既要覆盖 RocketMQ 的基础概念，也要通过真实订单场景讲清楚可靠消息问题。代码面向第一次学习
RocketMQ 的开发者，因此配置、生产者、消费者、Controller、Service、Mapper XML、SQL 和部署文件都要包含
有业务含义的中文注释。

本次替换后，项目中不再保留旧消息模块的依赖、配置、Java 包、Mapper、接口、文档或运行资源。

## 2. 技术选择

### 2.1 Spring 集成

优先使用 Apache RocketMQ 官方提供的：

```xml
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-v5-client-spring-boot-starter</artifactId>
    <version>2.3.6</version>
</dependency>
```

Starter 用于演示 Spring Boot 自动配置、`RocketMQClientTemplate` 和监听容器。Starter 没有完整覆盖、或者为了讲清
底层机制而需要显式控制的高级功能，可以直接使用其底层 RocketMQ 5.x gRPC Java Client API。

当前项目使用 Spring Boot 4.1，而该 Starter 的官方构建基线仍较旧，因此代码边界要集中在 RocketMQ 配置层和发布适配层，
避免业务 Service 直接依赖大量 Starter 内部类型。如果用户运行时发现确定的兼容问题，可以只替换适配层，不改业务流程。

### 2.2 RocketMQ 运行环境

在 `/Users/xiongtao/workspace/backend/docker/docker-compose.yml` 中加入：

- NameServer：保存和发现 Broker 路由信息。
- Broker：持久化消息、维护 MessageQueue 和消费进度。
- Proxy：为 RocketMQ 5.x gRPC Client 提供访问入口。
- Dashboard：用于查看集群、Topic、ConsumerGroup、消息和消费进度。
- Init：等待服务可用后创建学习案例需要的 Topic 和 ConsumerGroup。

使用独立的 RocketMQ 网络和持久化卷，不修改现有 PostgreSQL、Redis、MongoDB 服务。主机端口避开 Spring Boot 常用的
`8080`，应用通过本机 Proxy 地址连接，容器之间通过 Compose 服务名连接。

## 3. 旧消息模块清理边界

删除以下旧消息模块专属内容：

- 旧客户端与流式客户端依赖。
- 旧连接配置和业务配置。
- 旧 Java 业务包与 Mapper XML。
- 旧学习指南、建表脚本、设计文档和实施计划。
- 本机旧消息服务的容器、镜像、网络和数据卷。

`mq_outbox_event`、`mq_consumed_message`、`mq_order_statistics`、`mq_notification_log`、`mq_transaction_record`
表名是通用消息业务命名，因此继续保留或新增，并把其中旧组件专属注释改为通用可靠消息或
RocketMQ 语义。

用户当前未提交的 MyBatis SQL 日志配置修改与本任务无关，必须原样保留。

## 4. RocketMQ 核心模型

### 4.1 Topic 与消息类型

RocketMQ 5.x 的 Topic 按消息类型区分。本模块分别创建：

- NORMAL：普通消息、Tag 过滤、并发消费和批量案例。
- FIFO：同一个订单 ID 对应同一个 MessageGroup，保证订单内事件有序。
- DELAY：订单超时检查和自定义延迟消息。
- TRANSACTION：RocketMQ 半消息、本地事务和事务状态回查。

不把四种消息混在一个 Topic 中，避免学习者误以为一个 Topic 可以随意切换消息类型。

### 4.2 Tag、Key 和 ConsumerGroup

- Topic 表示一类业务消息，例如订单领域事件。
- Tag 表示 Topic 内的二级分类，例如 `ORDER_CREATED`、`ORDER_PAID`、`ORDER_CANCELLED`。
- Key 使用业务可查询标识，例如消息业务 ID 或订单号，便于 Dashboard 定位消息。
- 同一个 ConsumerGroup 内的实例共同分担消息。
- 不同 ConsumerGroup 各自维护消费进度，因此都可以收到同一条消息，形成发布订阅效果。

文档和注释需要分别讲清 Topic、Tag、Key、ConsumerGroup 与订阅过滤的职责，避免把这些概念混为一谈。

## 5. 学习功能

### 5.1 基础消息入口

提供独立接口演示：

- 同步发送：等待 `SendReceipt`，返回 Broker 接收后的 messageId。
- 异步发送：立即返回业务追踪 ID，完成回调记录成功或失败。
- Tag 过滤：同一个 Topic 使用不同 Tag，由不同订阅表达式过滤。
- 多消费组：统计消费者和通知消费者分别接收同一订单事件。
- 集群消费：同组多个实例只由其中一个实例处理同一条消息。
- FIFO：用订单 ID 作为 MessageGroup，演示创建、支付、发货的订单内顺序。
- DELAY：设置投递时间，演示指定时间后才对消费者可见。
- 重试和 DLQ：消费失败返回失败结果，达到最大重试次数后进入死信队列。
- 批量请求和批量处理：接口一次接收多条业务消息，由发布适配层逐条或受控并发发送，并分别记录每条发送结果；
  注释明确区分“应用层批量请求”和“Broker 单次批量发送”，不假定所有 5.x gRPC Client 版本都提供相同的批量 API。
- 消息版本：统一信封携带 `messageId`、`eventType`、`schemaVersion`、`aggregateId`、`occurredAt`、`payload`。

### 5.2 Outbox 可靠订单入口

接口：

```text
POST /api/playground/rocketmq/orders/outbox
```

同一个 PostgreSQL 本地事务内执行：

1. 校验用户、商品和购买数量。
2. 按商品 ID 排序，降低并发订单锁顺序不一致导致死锁的概率。
3. 使用带库存条件的 SQL 原子扣减库存。
4. 创建订单和订单明细。
5. 插入 `mq_outbox_event`，状态为 `PENDING`。
6. 提交数据库事务。

定时发布器使用 `FOR UPDATE SKIP LOCKED` 分批领取待发送事件，发送到 RocketMQ 后根据结果标记为
`PUBLISHED` 或安排退避重试。发布成功但更新 Outbox 状态前宕机会导致重复发送，因此消费者必须幂等。

### 5.3 RocketMQ 事务消息入口

接口：

```text
POST /api/playground/rocketmq/orders/transaction-message
```

该入口与 Outbox 入口完全独立：

1. 在独立短事务持久化 PREPARED 回查锚点。
2. 向 TRANSACTION Topic 发送半消息，Broker 保存但暂时不投递。
3. 事务监听器执行订单创建、库存扣减等 PostgreSQL 本地事务。
4. 本地事务成功返回 COMMIT，失败返回 ROLLBACK。
5. 结果不明确时返回 UNKNOWN，Broker 后续调用事务回查。
6. 回查逻辑只依赖数据库中可持久化的事务业务状态，不能依赖内存变量；过期 PREPARED 必须先用条件更新
   抢占 ROLLED_BACK 终态，抢占失败则重读最新状态，不能根据旧快照直接裁决。

新增 `mq_transaction_record` 保存事务 ID、业务键和 `PREPARED`、`COMMITTED`、`ROLLED_BACK` 状态。发送半消息前先准备
可查询的事务标识；本地订单事务成功时把记录改为 `COMMITTED`，明确失败时改为 `ROLLED_BACK`。Broker 回查时以这张表和
订单事实为依据；仍在合理执行窗口内的 `PREPARED` 返回 UNKNOWN，超过保护窗口且没有订单事实时才按明确规则回滚。

本地事务方法抛异常也不能直接等同于“数据库已回滚”：COMMIT 可能已经成功，只是响应因连接中断没有返回。
异常分支必须先以 `PREPARED -> ROLLED_BACK` 条件更新竞争终态；更新失败后重读，`COMMITTED` 提交半消息、
`ROLLED_BACK` 回滚半消息、状态仍不明确则保持半消息未决等待 Broker 回查。

还必须覆盖“PREPARED 已提交，半消息尚未到 Broker 就崩溃”的窗口：Broker 没有半消息就不可能回查。
应用定时分批读取过期 PREPARED 候选，再对每条执行与 checker 相同的条件回滚；0 行时重读并仅接受
COMMITTED/ROLLED_BACK 持久终态。多实例、checker 和本地事务因此都由同一数据库状态机裁决。

`business_key` 使用仅覆盖 PREPARED/COMMITTED 的 PostgreSQL 部分唯一索引。因此活跃或已成功事务拒绝同 orderNo 重复，
ROLLED_BACK 则保留审计记录并释放新 transactionId 的受控重试资格。旧全局唯一约束与新索引的迁移必须在同一事务块中幂等执行。

同一笔订单不能同时使用 Outbox 和 RocketMQ 事务消息，否则会产生两套发布路径和重复事件。两个入口的目的只是学习
两种解决数据库与 MQ 双写一致性的方案。

### 5.4 延迟关闭订单

订单创建完成后发送 DELAY 消息，投递时间默认为创建后 30 分钟。消费者收到消息时不能直接关闭订单，而要：

1. 查询订单是否仍存在。
2. 使用条件更新将 `UNPAID` 改为 `CANCELLED`。
3. 只有条件更新成功的处理者才能恢复库存。
4. 已支付、已取消或已经处理的订单直接幂等返回成功。

支付与超时关闭并发时，双方通过数据库条件更新竞争订单状态，不能只依靠消息到达先后判断业务结果。

## 6. 消费可靠性

### 6.1 至少一次投递与幂等

RocketMQ 的消费重试意味着同一消息可能被多次投递。`mq_consumed_message` 使用
`(consumer_name, message_id)` 唯一约束作为最终并发兜底：

- 首次消费成功插入幂等记录并执行业务。
- 重复消费无法再次插入，直接返回消费成功。
- 幂等记录与消费者业务变更放在同一个 PostgreSQL 本地事务中。

只在处理完成后确认消费成功；如果业务事务失败，返回消费失败，让 Broker 重试。

### 6.2 重试、死信和人工恢复

- 可恢复异常返回失败，由 Broker 按策略重新投递。
- 不支持的消息版本、无法解析的消息也先进入有限次重试，再由 DLQ 收口。
- 达到最大次数后进入对应 ConsumerGroup 的死信队列。
- 运维查询接口展示消费记录、Outbox 状态、统计记录和通知记录。
- 学习文档说明如何在 Dashboard 中查找重试消息和死信消息，以及人工修复后重新投递的注意事项。

### 6.3 乱序与过期消息

- 需要严格订单内顺序的事件使用 FIFO Topic 和相同 MessageGroup。
- 普通并发消息不能假定业务完成顺序等于发送顺序。
- 状态变更使用条件更新，拒绝把已支付订单改回未支付等非法倒退。
- 消息信封保存 `occurredAt` 和版本号，消费者可以识别明显过期事件。

## 7. 模块结构

新模块位于：

```text
com.xt.xiaoxingxing.playground.rocketmq
```

按职责拆分：

- `config`：Starter、自定义属性、客户端、Topic 名称和消费者配置。
- `controller`：基础消息、Outbox订单、事务消息和运维观察入口。
- `producer`：统一消息构建、同步/异步发送、FIFO、延迟和事务发送。
- `consumer`：普通、Tag、FIFO、延迟、统计、通知和失败重试消费者。
- `transaction`：RocketMQ 本地事务执行与事务回查。
- `service`：订单业务、Outbox、消费幂等和查询组装。
- `schedule`：Outbox 定时领取与发布。
- `mapper`、`entity`：PostgreSQL 持久化。
- `message`、`dto`、`vo`：接口和消息模型，不直接暴露持久化实体。
- `support`：编解码、版本校验、发布结果和共享辅助逻辑。

## 8. 注释标准

注释必须解释原因、边界和失败路径，不能只把代码翻译成中文：

- 类注释：说明该类在 RocketMQ 整体流程中的职责和依赖。
- 配置注释：解释 NameServer、Broker、Proxy、Topic、ConsumerGroup 等概念。
- 方法注释：说明输入、返回值、消息何时被认为发送或消费成功。
- 复杂 Service：方法开头先写完整步骤，再用“第1步、第2步……”对应实现。
- 关键语句：解释 MessageGroup、Tag、Key、消费结果、唯一约束和条件更新的目的。
- 异常分支：解释为什么应该重试、为什么应该幂等返回、什么时候进入 DLQ。
- 对比注释：必要处说明 RocketMQ 与旧消息模型的区别，但不保留旧业务代码。
- 生产提示：指出单节点 Compose 只适合学习，不代表生产高可用部署。

## 9. 文档与调用顺序

新增 RocketMQ 学习指南，至少包含：

- Docker Compose 启停和 Dashboard 地址。
- NameServer、Broker、Proxy、Topic、MessageQueue、Tag、Key、ConsumerGroup 概念。
- 四种 Topic 类型及初始化命令。
- 每个接口的请求样例、预期响应和 Dashboard 观察位置。
- 同组负载均衡与多组各收一份消息的对比。
- Outbox 与事务消息的流程图和选型表。
- 重试、DLQ、幂等、顺序、过期和延迟消息说明。
- PostgreSQL 表查询语句和故障演练步骤。

## 10. 验收标准与验证边界

静态验收：

- 项目中没有旧消息模块的依赖、配置、包名、Mapper 路径和学习文档残留。
- RocketMQ 的 Controller、生产、消费、Outbox、事务消息和 Mapper 引用链完整。
- Topic 类型与消息发送方式匹配。
- 所有消费者使用稳定、明确的 ConsumerGroup。
- Outbox 和消费幂等仍有数据库唯一约束与事务边界。
- Compose 中原有数据库服务未被破坏，RocketMQ 服务、网络和卷定义完整。
- 复杂方法具有步骤式中文注释，关键可靠性代码具有原因与失败路径注释。

按照用户约束，不新增或运行测试，不执行 Maven 构建，也不启动应用。Docker 容器替换属于用户已经明确授权的运行环境
操作；容器和镜像可以重新创建或拉取，持久卷删除后其中的旧消息数据不可恢复。
