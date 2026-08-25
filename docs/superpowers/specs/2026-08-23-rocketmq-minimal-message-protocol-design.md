# RocketMQ 最小消息协议设计

## 目标

删除当前 RocketMQ 模块中同一事实在 Tag、JSON 信封、事务表和消费记录之间的重复传递，只保留真实业务链需要的数据，同时保留事务消息状态裁决、统计幂等和订单并发条件更新。

## 消息协议

- RocketMQ Tag 表示事件类型。
- RocketMQ Key 表示订单号 `orderNo`。
- 消息 Body 只保存应用生成的稳定 `transactionId`；延迟检查直接沿用来源 CREATE 的 transactionId。
- 删除通用 JSON 信封、`eventType`、`aggregateId`、`occurredAt`、`schemaVersion` 和泛型 `payload`。
- CREATE、PAY、CANCEL 和付款超时检查都使用同一最小协议，不再发送无人消费的命令 DTO。

## 消费链

- 各 Listener 只解析自身使用的 Tag、Key 或 Body `transactionId`。
- 订单统计通过 Tag 得到 `OrderOperation`，通过 Key 查询订单，通过 `transactionId` 做数据库幂等。
- 商品缓存删除通过 Key 查询订单商品并删除 Redis；删除天然幂等，不再写消费幂等记录。
- 付款超时调度直接使用 Key 中的订单号，不再重复查询事务记录证明消息已提交。
- 付款超时检查只使用订单号查询订单，不读取 Body，也不再同时传递和校验 `orderNo`、`orderId`。

## 事务记录

- `mq_transaction_record` 只保存事务协调状态，不保存订单等业务字段。
- 事务记录只保留 `transaction_id`、`status`、`last_error`、`created_at` 和 `updated_at`。
- 保留 PREPARED 条件提交/回滚、Broker Checker 竞争失败后的重读和过期 PREPARED 清理。

## 消费记录与统计

- `mq_consumed_message` 只保留 `consumer_group`、`message_id`、`consumed_at`，组合主键负责幂等。
- 删除只写不读的 `event_type`、`aggregate_id` 和无业务引用的自增 `id`。
- `order_statistics.last_event_at` 改名为 `last_consumed_at`，与实际写入的消费完成时间一致。

## 配置与代码边界

- 删除属性类和环境配置中的 `enabled`；模块发送端、Checker 与消费者必须作为一条完整链路存在，不能只关闭其中一半。
- 30 秒清理周期和 Broker 最小 1 秒延迟作为代码规则，不再暴露没有实际覆盖的隐藏配置项。
- 保留 Producer/Listener SSL 同步增强器，因为它解决 Starter 2.3.6 的真实注解限制。

## 验证边界

- 按用户要求不运行测试或构建。
- 使用静态引用搜索确认删除类型和旧字段没有残留。
- 对 `demo` 数据库先查询现状，再执行事务化 ALTER，最后查询列、主键、约束、索引和数据量确认结构实际生效。
