# RocketMQ Minimal Message Protocol Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 RocketMQ 消息收敛为 `Tag + orderNo Key + transactionId Body`。

**Architecture:** Broker 元数据承担事件路由和订单定位，Body 只承担应用消息幂等身份。普通消费者不再回查事务表证明消息已提交；Broker Checker 仍只依据持久事务状态裁决半消息。

**Tech Stack:** Java 21、Spring Boot 4.1、RocketMQ Client Java 5.0.7、MyBatis、PostgreSQL、Redis

**Spec:** `docs/superpowers/specs/2026-08-23-rocketmq-minimal-message-protocol-design.md`

## Global Constraints

- 不运行测试或构建；只执行静态搜索和数据库结构查询验证。
- 保留工作区中已有且与本任务无关的修改。
- 数据库只对 `demo.public` 中三个明确的 RocketMQ 表执行目标迁移，不执行 `schema-demo.sql` 的 DROP/重建脚本。
- 事务状态竞争、统计幂等、订单状态条件更新和 SSL AnnotationEnhancer 必须保留。

---

### Task 1: 简化消息协议和发布/解码边界

**Files:**
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/message/RocketMessageEnvelope.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/message/CreateOrderMessage.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/message/OrderItemMessage.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/message/PayOrderMessage.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/message/CancelOrderMessage.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/util/RocketMessageCodec.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/util/RocketMqUtil.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/enums/OrderOperation.java`

**Interfaces:**
- Produces: `RocketMessageCodec.decodeTransactionId(MessageView)`、`RocketMqUtil` 的 Tag/Key/payload 参数协议、`OrderOperation.fromTag(String)`。

- [ ] 删除 JSON 信封和命令 payload 类型。
- [ ] 将 Codec 缩减为 UTF-8 Body transactionId 解码。
- [ ] Publisher 直接发送 transactionId 字符串并设置订单号 Key。
- [ ] 将 `eventType()`/`fromMessage()` 改为 `tag()`/`fromTag()`。

### Task 2: 简化 Listener 和业务消费逻辑

**Files:**
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/listener/*.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/message/OrderEventPayload.java`
- Delete: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/OrderEventService.java`
- Create: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/OrderStatisticsService.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/ProductService.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/service/OrderService.java`

**Interfaces:**
- Consumes: Tag、唯一订单 Key、transactionId。
- Produces: `OrderStatisticsService.record(transactionId, operation, orderNo)`、`ProductService.evictOrderProducts(orderNo)`、`OrderService.cancelExpiredOrder(orderNo)`。

- [ ] 通用消费支持解析 Tag、Key 和 transactionId。
- [ ] 删除单 Tag Listener 的重复路由校验。
- [ ] 将统计与缓存职责拆开并删除综合中转 DTO。
- [ ] 删除普通消费者对事务记录的重复事实校验和超时双订单标识。

### Task 3: 简化事务和消费持久化代码

**Files:**
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/entity/MqTransactionRecord.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/entity/MqConsumedMessage.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/repository/TransactionRecordRepository.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/checker/OrderTransactionChecker.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/task/PreparedTransactionCleanupTask.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/mapper/*.java`
- Modify: `src/main/resources/mapper/rocketmq/*.xml`

**Interfaces:**
- Produces: transactionId/orderNo/operationType 事务记录以及 consumerGroup/messageId 消费幂等记录。

- [ ] 删除 businessType 和旧字段命名。
- [ ] 内联一次性 Repository 包装并把 `prepare` 改为 `void`。
- [ ] 将 COUNT 提交判断改为 EXISTS。
- [ ] 删除清理任务中只改变日志的竞争失败重读，保留 Checker 的裁决重读。

### Task 4: 同步 DDL、配置和真实数据库

**Files:**
- Modify: `docs/schema-demo.sql`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/config/RocketMqConstants.java`
- Modify: `src/main/java/com/xt/xiaoxingxing/playground/rocketmq/config/RocketMqLearningProperties.java`

**Interfaces:**
- Produces: 与 Java/Mapper 一致的 PostgreSQL 表结构。

- [ ] 更新 schema-demo.sql 的三个表、注释、约束和索引。
- [ ] 删除恒定业务类型和没有实际读取/覆盖的配置字段。
- [ ] 在单个数据库事务内迁移真实 `demo` 表。
- [ ] 查询 information_schema、pg_constraint、pg_indexes 和行数确认迁移结果。
- [ ] 使用 `rg` 检查删除符号和旧列名没有运行代码残留，并查看最终 diff；不运行测试或构建。
