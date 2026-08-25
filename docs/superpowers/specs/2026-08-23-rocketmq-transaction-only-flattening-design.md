# RocketMQ 单事务消息方案扁平化设计

## 目标

删除 Transactional Outbox 的全部运行代码、配置、SQL 和当前学习文档内容，只保留 RocketMQ 事务消息订单链。`rocketmq` 模块不再按 `order/outbox`、`order/transaction` 区分实现，而是直接使用统一的技术分层。

## 包结构

```text
com.xt.xiaoxingxing.playground.rocketmq
├── config
├── controller
├── dto
├── entity
├── mapper
├── message
├── repository
├── service
├── listener
├── checker
└── task
```

不再保留 `rocketmq.order`、`rocketmq.product`、`rocketmq.support`、`rocketmq.infrastructure` 或机制名称为 `outbox/transaction` 的业务子包。

## 保留的业务链

1. HTTP 创建或支付订单。
2. 独立事务写入 `mq_transaction_record=PREPARED`。
3. 向 Broker 发送事务半消息。
4. PostgreSQL 本地事务写订单、明细、库存事实，并将事务记录条件推进为 `COMMITTED`。
5. 本地事务结束后提交半消息；RPC 结果不明确时由 Broker Checker 根据事务记录裁决。
6. 消费端处理缓存失效、订单统计和付款超时调度；延迟消息到期后执行事务式取消。
7. 清理任务收口 Broker 尚未收到半消息时遗留的过期 `PREPARED`。

## 数据与 DTO 边界

RocketMQ 模块拥有自己的 `CreateOrderRequest`、`CreateOrderItemRequest`、`OrderResponse`、`ProductResponse`、`Order`、`OrderItem` 和 `Product`，不再依赖 PostgreSQL 学习模块的 HTTP DTO、实体、VO 或 `PgMyBatisService`。订单相关 SQL 收口到 `rocketmq.mapper.OrderMapper`。

消息载荷不使用 `Command` 容器：`CreateOrderMessage`、`OrderItemMessage`、`PayOrderMessage`、`CancelOrderMessage` 都是独立顶层 DTO，位于 `message` 包。

## 配置原则

- Topic、ConsumerGroup 和注解所需的事件字符串进入 `RocketMqConstants`；订单操作与事件的映射由 `OrderOperation` 枚举维护，订阅表达式直接在 Listener 注解中组合。
- YAML 只保留 RocketMQ 连接信息，以及确有环境差异的业务覆盖值。
- Java 提供订单超时、事务记录保护期、清理周期、缓存 TTL 等默认值。
- 保留开发环境现有的 `order-timeout-millis: 100000` 覆盖。
- 不恢复用户已经删除的配置交叉校验。

## 删除范围

- `order/outbox` 下全部 Java 文件。
- `OutboxRelay`、`MqOutboxEvent`、`MqOutboxEventMapper` 及其 XML。
- `mq_outbox_event` 的建表、索引和说明；全量重建脚本只保留一条兼容旧库的清理 DROP。
- YAML 中普通事件 Topic、Outbox Tag、三个 Outbox ConsumerGroup 和 Relay 重试配置。
- 当前 RocketMQ 学习文档中的双方案对照、Outbox 章节、接口和排障 SQL。
- 共享类中仅供 Outbox 使用的普通消息发布、随机信封 ID 和 Outbox JSON 恢复入口。

## 保留边界

- 保留 `mq_transaction_record`、`mq_consumed_message`、`order_statistics`。
- 保留缓存失效、统计、超时调度、超时取消四个事务消息消费者。
- 保留 PostgreSQL、Redis 和 RocketMQ 的项目级连接设施。
- ConsumerGroup 使用不带项目缩写和版本号的最终业务名称；从旧名称迁移时必须先停写并处理历史积压，不能假设消费位点自动继承。

## 校验取舍

保留外部请求校验、消息解码、消费幂等、事务状态裁决、条件状态更新和库存条件扣减。删除查询主键后再次比较同一主键、同一调用链重复验证已构造字段以及只为防御手工改库而存在的重复检查。

## 验证边界

根据仓库要求，本次不创建测试、不运行测试或构建。完成后仅使用静态搜索、目录清单和 `git diff` 核对是否仍存在 Outbox 引用、旧包引用、失效配置和意外覆盖用户改动。
