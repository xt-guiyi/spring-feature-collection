# RocketMQ 可维护性收口设计

## 目标

在现有“只保留 RocketMQ 事务消息”的结构上继续收口职责，删除不完整的开关和无行为用途的数据，同时保留正常并发、网络结果不明确所必需的事务判断。

## 设计决定

1. 删除 `playground.rocketmq.enabled` 及全部 `@ConditionalOnProperty`。当前案例没有第二种运行模式，半开半关会产生“能发送、不能回查/消费”的错误状态。
2. `OrderService` 只负责订单生命周期与事务半消息协调。CREATE 消息消费后的延迟调度移动到一个具体类 `PaymentTimeoutScheduler`，不增加接口、实现类或 Facade。
3. PAY 入口必须确认该订单存在已提交的 CREATE 事务记录。`orders` 是共享表，其他 PostgreSQL 学习接口也会正常创建 PENDING 订单，因此这是模块所有权边界，不是防止手工改库。
4. CREATE/PAY/CANCEL 的同一业务操作已经 COMMITTED 时，重复请求返回当前订单结果；仍为 PREPARED 时明确提示处理中；ROLLED_BACK 不占用部分唯一索引，可以重新发起。
5. `MqConsumerRecordMapper` 只操作 `mq_consumed_message`；`order_statistics` UPSERT 移入独立 `OrderStatisticsMapper`。两个 Mapper 继续由同一个 `TransactionTemplate` 保证原子提交。
6. 延迟消息 Body 直接沿用来源 CREATE 的 transactionId，Key 继续使用 orderNo。
7. 删除只转发一行调用的 Listener 私有方法；`OrderOperation.fromTag` 从枚举已有 tag 字段查找，不再维护第二份 switch 映射。
8. 商品缓存接受 TTL 范围内的陈旧查询视图；数据库条件扣减仍是唯一库存裁决。这里不引入版本化缓存、Lua 或延迟双删，避免为了展示一致性扩大系统复杂度。
9. `schema-demo.sql` 保留一条清理旧 `mq_outbox_event` 的 DROP。它只服务旧库全量重建，不重新引入 Outbox 运行逻辑。

## 验证边界

遵循仓库要求，不新增或运行测试，不运行构建。只进行静态搜索、XML/Java 方法对照、配置与文档对照、`git diff --check` 和最终 diff 审查；最终说明没有运行时证明。
