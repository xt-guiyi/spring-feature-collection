# RocketMQ Maintainability Hardening Implementation Plan

> **For agentic workers:** Follow this plan task-by-task. The repository explicitly forbids tests/builds for this change; use the static checks listed below.

**Goal:** 删除半开关和无效协议字段，恢复订单所有权与正常重试幂等，并把超时调度、统计持久化从不相干职责中拆出。

**Architecture:** `OrderService` 保留订单事务消息协调，新增单一具体类 `PaymentTimeoutScheduler` 承担 CREATE 消费后的延迟调度。消费幂等与统计投影使用两个 Mapper，但共享一个本地事务。

**Tech Stack:** Java 21、Spring Boot、RocketMQ 5.x Client、MyBatis、PostgreSQL、Redis

**Spec:** `docs/superpowers/specs/2026-08-23-rocketmq-maintainability-hardening-design.md`

## Global Constraints

- 不增加 `interface/impl`、Facade 或通用工具层。
- 不新增或运行测试，不运行 Maven 构建。
- 不覆盖 RocketMQ 模块之外的用户改动。
- 所有数据库并发裁决继续使用条件 UPDATE 和唯一索引。

---

### Task 1: 删除不完整的 enabled 开关

**Files:** `application.yaml`、四个 Listener、Checker、CleanupTask、AnnotationEnhancer、当前 RocketMQ 文档。

- [x] 删除 YAML 的 `enabled`。
- [x] 删除全部 `@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled")` 及 import。
- [x] 文档只保留 endpoints、SSL 和真实环境覆盖。

### Task 2: 订单来源与操作幂等

**Files:** `OrderService.java`、`TransactionRecordRepository.java`、`MqTransactionRecordMapper.java/xml`。

- [x] 新增按 `orderNo + operationType` 查询活跃 PREPARED/COMMITTED 记录的方法。
- [x] PAY 前要求 CREATE 记录为 COMMITTED。
- [x] `prepare` 唯一冲突离开 `REQUIRES_NEW` 回滚后，由调用方重读活跃记录：COMMITTED 返回订单，PREPARED 返回处理中，无法定位才报告真实冲突。
- [x] 合并同商品数量时使用 `long` 中间值，拒绝真实请求可触发的 `int` 溢出。

### Task 3: 拆出付款超时调度

**Files:** 新建 `PaymentTimeoutScheduler.java`，修改 `OrderService.java`、`PaymentTimeoutScheduleListener.java`、`RocketMqUtil.java`。

- [x] 移动调度消费幂等、延迟计算和事务模板。
- [x] Body 直接使用 `createdTransactionId`。
- [x] `OrderService` 只保留订单 CREATE/PAY/CANCEL 与半消息裁决。

### Task 4: 拆分统计 Mapper

**Files:** 新建 `OrderStatisticsMapper.java/xml`，修改 `MqConsumerRecordMapper.java/xml`、`OrderStatisticsService.java` 和目录文档。

- [x] `MqConsumerRecordMapper` 只保留幂等表方法。
- [x] `OrderStatisticsMapper` 只保留统计 UPSERT。
- [x] 两个调用继续位于同一个 `TransactionTemplate` 回调内。

### Task 5: 删除剩余无意义间接层并同步文档

**Files:** `PaymentTimeoutListener.java`、`ProductCacheInvalidationListener.java`、`OrderOperation.java`、`schema-demo.sql`、当前设计/学习文档。

- [x] 内联三个一行 handler。
- [x] `fromTag` 遍历枚举现有映射。
- [x] 全量重建脚本补回旧 Outbox 表的清理 DROP。
- [x] 明确缓存是允许 TTL 范围内陈旧的查询视图，并同步最终目录和流程说明。
- [x] 明确两个 Topic 的 `TRANSACTION` / `DELAY` 类型，以及超时调度允许重复发送但不会漏发的边界。

### Task 6: 静态验证

- [x] 搜索 `playground.rocketmq.enabled`、`ConditionalOnProperty`、旧派生 timeout transactionId 和旧统计 Mapper 方法引用。
- [x] 对照 Java Mapper 与 XML statement id。
- [x] 对照 Java 常量与 Compose Topic/ConsumerGroup。
- [x] 运行 `git diff --check` 并审查最终 diff；不宣称编译或运行通过。
