# RabbitMQ Reliable Messaging Learning Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 在 playground 中实现覆盖三种队列、可靠订单、Outbox、确认、重试、死信、延迟、幂等、顺序和 Stream 回放的 RabbitMQ 中文学习模块。

**Architecture:** Classic Queue 承担基础路由和 ACK 行为演示；Quorum Queue 承担可靠订单消费者、重试和死信；Stream 保存订单事件审计日志。订单与 Outbox 使用同一个 PostgreSQL 本地事务，发布器等待 Publisher Confirm 并检查 Mandatory Return，消费者手动 ACK 且通过 PostgreSQL 唯一键实现幂等。

**Tech Stack:** Java 21、Spring Boot 4.1.0、Spring AMQP 4.1、RabbitMQ 4、RabbitMQ Stream Java Client、MyBatis XML、PostgreSQL、Redis。

## Global Constraints

- 所有用户可见说明和关键代码注释使用中文。
- 复杂 Service 方法必须先写完整步骤，再按“第1步、第2步……”实现。
- 不编写测试文件，不运行测试、Maven 构建或应用启动；只执行静态检查。
- 不修改或复制现有 `PgMyBatisService.createCompleteOrder` 的业务计算。
- 不使用 RabbitMQ Delayed Message Plugin；30 分钟延迟使用 TTL + DLX。
- 不引入分布式事务，保留至少一次投递语义和消费者幂等兜底。

---

## File Map

### Existing files to modify

- `pom.xml`：加入 AMQP Starter 和 Spring Rabbit Stream。
- `src/main/resources/application.yaml`：加入 RabbitMQ、监听器、Stream 和 Outbox 参数。
- `src/main/resources/application-dev.yaml`：保持开发配置与默认配置一致。
- `src/main/java/com/xt/xiaoxingxing/shared/config/PlaygroundMyBatisConfig.java`：扫描 RabbitMQ Mapper。
- `docs/schema-demo.sql`：把 MQ 学习表纳入完整 demo 重建脚本。

### New configuration and message files

- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/config/RabbitMqNames.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/config/RabbitMqLearningProperties.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/config/RabbitMqTopologyConfig.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/config/RabbitStreamConfig.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/message/RabbitMessageEnvelope.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/message/OrderEventPayload.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/message/DemoMessagePayload.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/support/RabbitMessageCodec.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/support/RabbitPublishResult.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/support/ReliableRabbitPublisher.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/support/RabbitConsumerSupport.java`

### New PostgreSQL persistence files

- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/entity/MqOutboxEvent.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/entity/MqConsumedMessage.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/entity/MqOrderStatistics.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/entity/MqNotificationLog.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/mapper/MqOutboxEventMapper.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/mapper/MqConsumerRecordMapper.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/mapper/MqOrderBusinessMapper.java`
- `src/main/resources/mapper/rabbitmq/MqOutboxEventMapper.xml`
- `src/main/resources/mapper/rabbitmq/MqConsumerRecordMapper.xml`
- `src/main/resources/mapper/rabbitmq/MqOrderBusinessMapper.xml`
- `docs/rabbitmq-reliable-messaging-schema.sql`

### New services, consumers and web API files

- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/service/OutboxEventService.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/service/RabbitOrderApplicationService.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/service/RabbitOrderConsumerService.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/service/RabbitMqDemoService.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/schedule/OutboxPublishScheduler.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/consumer/BasicDemoConsumers.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/consumer/OrderEventConsumers.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/consumer/OrderAuditStreamConsumer.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/controller/RabbitMqDemoController.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/controller/RabbitOrderController.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/controller/RabbitMqOperationsController.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/dto/request/RabbitRoutingMessageRequest.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/dto/request/RabbitAckDemoRequest.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/dto/request/RabbitOrderingDemoRequest.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/dto/request/RabbitStreamEventRequest.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/vo/RabbitMessagePublishVO.java`
- `src/main/java/com/xt/xiaoxingxing/playground/rabbitmq/vo/RabbitOrderCreateVO.java`
- `docs/rabbitmq-reliable-messaging-learning.md`

---

### Task 1: Dependencies and connection configuration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/main/resources/application-dev.yaml`

**Interfaces:**
- Produces: Boot-managed `RabbitTemplate`, AMQP connection factory and listener configuration.
- Produces: `playground.rabbitmq.*` values for Stream, timeout, retry and Outbox scheduling.

- [x] **Step 1:** Add `spring-boot-starter-amqp` and `spring-rabbit-stream` without hard-coding versions already managed by Spring Boot 4.1.
- [x] **Step 2:** Configure `spring.rabbitmq` host, port, credentials, correlated confirms, returns, mandatory publishing, manual listener ACK, prefetch and concurrency.
- [x] **Step 3:** Add overridable `playground.rabbitmq` properties for 30-minute timeout, retry delay/count, confirm timeout, Outbox batch size/fixed delay and native Stream port 5552.

### Task 2: Queue names, properties and topology

**Files:**
- Create: `RabbitMqNames.java`
- Create: `RabbitMqLearningProperties.java`
- Create: `RabbitMqTopologyConfig.java`
- Modify: `XiaoxingxingApplication.java` only if scheduling is not enabled in the RabbitMQ configuration.

**Interfaces:**
- Produces: constants for every exchange, queue, binding and routing key.
- Produces: durable Classic, Quorum and Stream Queue beans plus exchange/binding beans.
- Produces: `rabbitManualContainerFactory` and `rabbitOrderedContainerFactory`.

- [x] **Step 1:** Define centralized constants so publishers and listeners cannot drift through duplicated strings.
- [x] **Step 2:** Bind Direct, Topic and Fanout learning queues and the ACK/order queues as Classic Queue.
- [x] **Step 3:** Bind cache, statistics, notification, timeout, retry and final dead-letter queues as Quorum Queue.
- [x] **Step 4:** Declare the 30-minute TTL delay queue and consumer-specific retry queues whose DLX targets the original queue through the default exchange.
- [x] **Step 5:** Declare `pg.order.audit.stream` with `QueueBuilder.stream()` and bind `order.#`.
- [x] **Step 6:** Configure manual ACK containers; ordered container uses prefetch=1 and one active consumer.

### Task 3: Message envelope, JSON codec and reliable publisher

**Files:**
- Create: `RabbitMessageEnvelope.java`
- Create: `OrderEventPayload.java`
- Create: `DemoMessagePayload.java`
- Create: `RabbitMessageCodec.java`
- Create: `RabbitPublishResult.java`
- Create: `ReliableRabbitPublisher.java`

**Interfaces:**
- Produces: `RabbitMessageEnvelope<JsonNode> RabbitMessageCodec.decode(Message message)`.
- Produces: `Message RabbitMessageCodec.encode(RabbitMessageEnvelope<?> envelope)`.
- Produces: `RabbitPublishResult ReliableRabbitPublisher.publishAndWait(String exchange, String routingKey, RabbitMessageEnvelope<?> envelope)`.

- [x] **Step 1:** Define the versioned envelope and payload objects with clear field semantics.
- [x] **Step 2:** Serialize with project `ObjectMapper`, set JSON content type, persistent delivery mode, message ID and schema version Header.
- [x] **Step 3:** Publish using `CorrelationData`, wait for confirm with configured timeout, and treat nack, timeout and `CorrelationData.getReturned()` as failures.
- [x] **Step 4:** Register a returns callback for learning logs while keeping per-message Return as the correctness check.

### Task 4: PostgreSQL MQ schema and Mapper scan

**Files:**
- Create: `docs/rabbitmq-reliable-messaging-schema.sql`
- Modify: `docs/schema-demo.sql`
- Modify: `PlaygroundMyBatisConfig.java`
- Create: four entity files and three Mapper interfaces listed in File Map.
- Create: three Mapper XML files listed in File Map.

**Interfaces:**
- Produces: `MqOutboxEventMapper.insert/claimPublishable/markPublished/markFailed/selectPage/countPage`.
- Produces: `MqConsumerRecordMapper.insertConsumedIfAbsent/upsertStatistics/insertNotification/select...`.
- Produces: `MqOrderBusinessMapper.selectOrderById/markPaid/markCancelled/selectOrderProducts/restoreStock`.

- [x] **Step 1:** Create all MQ tables with unique constraints and indexes; standalone SQL must be repeatable and non-destructive.
- [x] **Step 2:** Add entity classes whose Java property types match PostgreSQL columns.
- [x] **Step 3:** Implement Outbox atomic claim using `FOR UPDATE SKIP LOCKED` and `UPDATE ... RETURNING`.
- [x] **Step 4:** Implement idempotent consume insert with `ON CONFLICT DO NOTHING`, statistics upsert and notification log queries.
- [x] **Step 5:** Implement `PENDING -> PAID/CANCELLED` conditional updates and deterministic product-ID-order inventory restoration queries.
- [x] **Step 6:** Extend `@MapperScan` to include the new package without affecting existing PostgreSQL mappers.

### Task 5: Transactional Outbox service and scheduler

**Files:**
- Create: `OutboxEventService.java`
- Create: `OutboxPublishScheduler.java`

**Interfaces:**
- Produces: `String append(String aggregateId, String eventType, String exchange, String routingKey, Object payload)`.
- Produces: `List<MqOutboxEvent> claimPublishable()` in a short independent transaction.
- Consumes: `ReliableRabbitPublisher.publishAndWait(...)`.

- [x] **Step 1:** Build and persist the complete envelope as PostgreSQL JSONB inside the caller transaction.
- [x] **Step 2:** Claim a bounded batch in a short `REQUIRES_NEW` transaction so database locks are released before network publishing.
- [x] **Step 3:** Publish each claimed event and mark success only after confirm with no return.
- [x] **Step 4:** On failure, increment retry count, compute bounded exponential backoff, store the concise error, and move exhausted events to `DEAD`.
- [x] **Step 5:** Add the scheduled trigger and guard against overlapping runs in one application instance.

### Task 6: Reliable order application service

**Files:**
- Create: `RabbitOrderApplicationService.java`
- Create: `RabbitOrderCreateVO.java`

**Interfaces:**
- Produces: `RabbitOrderCreateVO createOrder(CompleteOrderCreateRequest request)`.
- Produces: `boolean payOrder(Long orderId)`.
- Consumes: `PgMyBatisService.createCompleteOrder`, `MqOrderBusinessMapper` and `OutboxEventService.append`.

- [x] **Step 1:** In one `playgroundTransactionManager` transaction call the existing complete-order method.
- [x] **Step 2:** Append `ORDER_CREATED` and `ORDER_PAYMENT_TIMEOUT_CHECK` Outbox rows before commit.
- [x] **Step 3:** Implement atomic payment transition and append `ORDER_PAID` only when the conditional update affects one row.
- [x] **Step 4:** Return order data plus the two created message IDs so the learner can inspect Outbox rows.

### Task 7: Consumer support, idempotent business handlers and manual ACK

**Files:**
- Create: `RabbitConsumerSupport.java`
- Create: `RabbitOrderConsumerService.java`
- Create: `OrderEventConsumers.java`

**Interfaces:**
- Produces: `void handle(Message message, Channel channel, String consumerName, String retryRoutingKey, Consumer<RabbitMessageEnvelope<JsonNode>> action)`.
- Produces: idempotent `handleCache/handleStatistics/handleNotification/handleTimeout` methods.

- [x] **Step 1:** Decode and reject unsupported schema versions as non-recoverable poison messages.
- [x] **Step 2:** ACK success, duplicates and stale order states only after the proxied business method returns and its transaction commits.
- [x] **Step 3:** Republish recoverable failures with an incremented retry Header; ACK original only after retry confirm succeeds.
- [x] **Step 4:** Use nack/requeue when retry publication itself fails, and reject without requeue after max attempts.
- [x] **Step 5:** Implement cache deletion, statistics upsert and notification simulation with per-consumer idempotency names.
- [x] **Step 6:** Implement timeout re-read, conditional cancel, product-ID-order stock restoration and `ORDER_CANCELLED` Outbox append in one transaction.

### Task 8: Classic Queue learning consumers and demo service

**Files:**
- Create: `RabbitMqDemoService.java`
- Create: `BasicDemoConsumers.java`
- Create: request and publish VO files listed in File Map.

**Interfaces:**
- Produces: methods for Direct, Topic, Fanout, Mandatory Return, ACK behavior, retry failure injection and ordered batch publishing.
- Consumes: `ReliableRabbitPublisher` and `RabbitConsumerSupport`.

- [x] **Step 1:** Implement Direct/Topic/Fanout publishing and manual-ACK consumers that log queue, exchange and routing key.
- [x] **Step 2:** Implement intentionally unroutable publishing and return the broker reply instead of hiding it.
- [x] **Step 3:** Implement one-time nack/requeue, reject-to-DLQ, retry-then-success and exhausted-retry examples without infinite loops.
- [x] **Step 4:** Publish sequence numbers and consume them through the single-active ordered queue, explaining why multiple consumers and redelivery can reorder observed completion.

### Task 9: Native RabbitMQ Stream example

**Files:**
- Create: `RabbitStreamConfig.java`
- Create: `OrderAuditStreamConsumer.java`
- Create: `RabbitStreamEventRequest.java`
- Modify: `RabbitMqDemoService.java`

**Interfaces:**
- Produces: `RabbitStreamTemplate rabbitStreamTemplate`.
- Produces: native `StreamRabbitListenerContainerFactory` configured with named consumer, first offset and manual offset tracking.

- [x] **Step 1:** Build native Stream `Environment` from `playground.rabbitmq.stream` properties.
- [x] **Step 2:** Send the same versioned JSON envelope with `RabbitStreamTemplate` and wait for its publish future.
- [x] **Step 3:** Consume native Stream messages, log offset, decode the envelope and call `context.storeOffset()` only after successful handling.

### Task 10: Controllers and observable query endpoints

**Files:**
- Create: `RabbitMqDemoController.java`
- Create: `RabbitOrderController.java`
- Create: `RabbitMqOperationsController.java`

**Interfaces:**
- Produces: `/api/playground/rabbitmq/demo/**`, `/stream/events`, `/orders/**` and operations GET endpoints.
- Consumes: existing `Result<T>` and `PageResult<T>` wrappers.

- [x] **Step 1:** Add validated requests for all basic routing, ACK, retry, ordering and Stream examples.
- [x] **Step 2:** Add reliable order creation and payment endpoints.
- [x] **Step 3:** Add paged Outbox and consumed-message endpoints plus statistics and notification-log endpoints.
- [x] **Step 4:** Keep AMQP framework objects and PostgreSQL entities out of public responses where a dedicated VO is clearer.

### Task 11: Local RabbitMQ Stream environment

**Files:**
- No repository file mutation beyond documented commands.

**Interfaces:**
- Produces: `local-rabbitmq` with ports 5672, 15672 and 5552 and plugin `rabbitmq_stream` enabled.

- [x] **Step 1:** Recreate the confirmed empty container with explicit ports and the existing `root/123456` credentials.
- [x] **Step 2:** Enable `rabbitmq_stream` and verify the broker reports AMQP and Stream listeners.

### Task 12: Learning guide and static verification

**Files:**
- Create: `docs/rabbitmq-reliable-messaging-learning.md`
- Inspect: all files in the RabbitMQ module and modified configuration files.

**Interfaces:**
- Produces: runnable learning order, curl examples, management UI observation guide, SQL queries and failure-scenario explanations.

- [x] **Step 1:** Document startup prerequisites, schema import, interface call order and expected queue/Outbox state transitions.
- [x] **Step 2:** Explain publisher confirms versus consumer ACK, at-least-once duplicates, TTL+DLX, quorum single-node limitation, Stream offsets and Outbox failure windows.
- [x] **Step 3:** Run `git diff --check`, Java import/reference `rg` checks, XML well-formedness checks and placeholder scans.
- [x] **Step 4:** Do not run Maven or tests; report that runtime and compilation remain for the user to verify.
