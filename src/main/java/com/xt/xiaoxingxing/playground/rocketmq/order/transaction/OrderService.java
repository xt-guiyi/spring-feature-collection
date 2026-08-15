package com.xt.xiaoxingxing.playground.rocketmq.order.transaction;

import tools.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderCreateRequest;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderItemRequest;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import com.xt.xiaoxingxing.playground.postgresql.service.PgMyBatisService;
import com.xt.xiaoxingxing.playground.postgresql.vo.CompleteOrderResponse;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqConsumedMessage;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.rocketmq.infrastructure.TransactionRecordRepository;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqConsumerRecordMapper;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqOrderBusinessMapper;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderEventPayload;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderTransactionCommands.CreateOrderCommand;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderTransactionCommands.OrderIdCommand;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderTransactionCommands.OrderItem;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.order.OrderResponse;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessageCodec;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessagePublisher;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessagePublisher.TransactionHandle;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * RocketMQ 事务消息版订单业务服务。
 *
 * <p>这个类收敛 CREATE、PAY、超时调度和 CANCEL 四段完整业务生命周期。它没有类级
 * {@code @Transactional}：PREPARED、发送半消息、本地订单事务、提交/回滚半消息属于不同可靠性窗口，
 * 若把整个方法包进数据库事务，会在 RocketMQ 网络调用期间长期占用连接和订单/库存行锁。</p>
 *
 * <p>每次 CREATE、PAY 或 CANCEL 只生成一个 UUID，同时作为事务表 transactionId 与信封 messageId；
 * 三种订单消息的 aggregateId 与 RocketMQ Key 都统一使用稳定订单号 orderNo。数据库主键 orderId 只用于
 * 订单表写入和业务命令，不再承担跨系统事务关联职责。</p>
 */
@Slf4j
@Service("transactionOrderService")
public class OrderService {

    private final PgMyBatisService pgMyBatisService;
    private final MqOrderBusinessMapper orderBusinessMapper;
    private final MqConsumerRecordMapper consumerRecordMapper;
    private final TransactionRecordRepository transactionRecordRepository;
    private final RocketMessageCodec messageCodec;
    private final RocketMessagePublisher publisher;
    private final RocketMqLearningProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readTransactionTemplate;

    public OrderService(PgMyBatisService pgMyBatisService,
                        MqOrderBusinessMapper orderBusinessMapper,
                        MqConsumerRecordMapper consumerRecordMapper,
                        TransactionRecordRepository transactionRecordRepository,
                        RocketMessageCodec messageCodec,
                        RocketMessagePublisher publisher,
                        RocketMqLearningProperties properties,
                        @Qualifier("playgroundTransactionManager")
                        PlatformTransactionManager transactionManager) {
        this.pgMyBatisService = pgMyBatisService;
        this.orderBusinessMapper = orderBusinessMapper;
        this.consumerRecordMapper = consumerRecordMapper;
        this.transactionRecordRepository = transactionRecordRepository;
        this.messageCodec = messageCodec;
        this.publisher = publisher;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
    }

    /**
     * 创建订单的完整事务消息链。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>第1步：把 HTTP 请求复制成不可变 CREATE 命令，并生成唯一 transactionId；</li>
     *     <li>第2步：使用同一个 transactionId 创建信封 messageId，aggregateId 使用真实 orderNo；</li>
     *     <li>第3步：REQUIRES_NEW 独立写入 PREPARED，再向 Broker 发送暂不可见的半消息；</li>
     *     <li>第4步：只在 TransactionTemplate 内创建订单、明细、扣库存并标记 COMMITTED；</li>
     *     <li>第5步：数据库事务退出后提交半消息；commit RPC 不明确时仍返回已落库订单。</li>
     * </ol>
     */
    public OrderResponse createOrder(CompleteOrderCreateRequest request) {
        // 第1步：消息命令保存业务输入快照，不保存客户端金额；本地事务仍以 PostgreSQL 商品价格为准。
        BusinessAssert.notNull(request, "创建订单请求不能为空");
        CreateOrderCommand command = new CreateOrderCommand(
                request.getOrderNo(),
                request.getUserId(),
                request.getItems() == null ? List.of() : request.getItems().stream()
                        .map(item -> new OrderItem(item.getProductId(), item.getQuantity()))
                        .toList());
        String transactionId = UUID.randomUUID().toString();

        // 第2步：一条操作只维护一个业务 UUID；CREATE 聚合标识是订单号，不再把 transactionId 塞入 aggregateId。
        RocketMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(
                transactionId, RocketMqNames.EVENT_ORDER_CREATED, command.orderNo(), command);

        // 第3至5步由统一协调模板完成；messageKey 使用真实订单号，便于 Dashboard 按业务对象查询。
        return executeTransaction(
                transactionId,
                RocketMqNames.OPERATION_CREATE,
                command.orderNo(),
                properties.getTags().getOrderCreated(),
                envelope,
                () -> transactionRecordRepository.prepare(
                        transactionId,
                        RocketMqNames.BUSINESS_ORDER,
                        command.orderNo(),
                        RocketMqNames.OPERATION_CREATE),
                () -> createOrderLocally(command),
                false);
    }

    /**
     * 支付事务消息订单。
     *
     * <p>先通过已提交 CREATE 记录确认订单来源，再让 PAY 的
     * {@code UPDATE orders ... WHERE status='PENDING'} 与超时 CANCEL 原子竞争。更新前 SELECT 只用于错误提示，
     * 真正并发裁决只能相信条件 UPDATE 的受影响行数。</p>
     */
    public OrderResponse payOrder(Long orderId) {
        // 第1步：HTTP 使用数据库主键定位订单，但跨系统事务键统一使用落库后的稳定 orderNo。
        PgOrder current = BusinessAssert.notNull(orderBusinessMapper.selectOrderById(orderId), "事务订单不存在");
        String orderNo = requireOrderNo(current);

        // 第2步：两套学习链路入口不能串用；订单存在不等于它由事务消息 CREATE 创建。
        BusinessAssert.isTrue(transactionRecordRepository.isCommitted(
                        RocketMqNames.BUSINESS_ORDER, orderNo, RocketMqNames.OPERATION_CREATE),
                "该订单不是事务消息链创建，不能使用事务消息支付入口");
        String transactionId = UUID.randomUUID().toString();
        OrderIdCommand command = new OrderIdCommand(orderId);

        // 第3步：PAY 的 aggregateId、RocketMQ messageKey 和事务 businessKey 全部使用 orderNo。
        RocketMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(
                transactionId, RocketMqNames.EVENT_ORDER_PAID, orderNo, command);
        return executeTransaction(
                transactionId,
                RocketMqNames.OPERATION_PAY,
                orderNo,
                properties.getTags().getOrderPaid(),
                envelope,
                () -> transactionRecordRepository.prepare(
                        transactionId,
                        RocketMqNames.BUSINESS_ORDER,
                        orderNo,
                        RocketMqNames.OPERATION_PAY),
                () -> payOrderLocally(orderId),
                false);
    }

    /**
     * 为已提交 CREATE 订单安排付款超时检查。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>第1步：短只读事务检查本消费组是否已经完成 createdMessageId；</li>
     *     <li>第2步：同一短事务校验 COMMITTED CREATE、订单事实并构造稳定延迟计划；</li>
     *     <li>第3步：退出 PostgreSQL 事务后同步发送 delay 消息；</li>
     *     <li>第4步：发送成功后再用短事务 insert-if-absent 写入完成记录；</li>
     *     <li>第5步：发送或完成记录任一结果不明确时，依靠稳定 timeout messageId 安全重试。</li>
     * </ol>
     *
     * <p>这里选择“允许重复，不能漏发”：并发消费者可能都在完成记录落库前发送同一个稳定 timeout messageId；
     * 发送成功但完成记录提交/回包不明确时也可能重发。重复 delay 最终由 CANCEL 活跃操作唯一约束与订单
     * {@code WHERE status='PENDING'} 条件更新收口，但数据库事务不会跨越 RocketMQ 网络调用。</p>
     */
    public void schedulePaymentTimeout(String orderNo, String createdMessageId) {
        BusinessAssert.isTrue(orderNo != null && !orderNo.isBlank(), "事务超时调度的orderNo不能为空");
        BusinessAssert.isTrue(createdMessageId != null && !createdMessageId.isBlank(),
                "CREATE消息ID不能为空");
        String consumerName = properties.getConsumerGroups().getTransactionTimeoutScheduler();

        // 第1步：只读检查和业务事实装载位于短事务中。完成记录存在时说明此前发送与落库都已经成功。
        TimeoutSchedulePlan plan = readTransactionTemplate.execute(status -> {
            if (consumerRecordMapper.existsConsumed(consumerName, createdMessageId)) {
                return null;
            }

            // 第2步（事务记录）：messageId 就是事务记录主键；不能再用 aggregateId 查询事务表。
            MqTransactionRecord record = BusinessAssert.notNull(
                    transactionRecordRepository.findById(createdMessageId), "CREATE事务记录不存在");
            BusinessAssert.isTrue(RocketMqNames.BUSINESS_ORDER.equals(record.getBusinessType())
                            && orderNo.equals(record.getBusinessKey())
                            && RocketMqNames.OPERATION_CREATE.equals(record.getOperationType())
                            && "COMMITTED".equals(record.getStatus()),
                    "CREATE消息与已提交事务记录不一致");

            // 第2步（订单事实）：延迟消息只是未来重新检查一次；当前已非 PENDING 时没有必要继续调度。
            PgOrder order = BusinessAssert.notNull(
                    orderBusinessMapper.selectOrderByOrderNo(orderNo), "事务订单不存在");
            Long orderId = order.getId();
            if (!"PENDING".equals(order.getStatus())) {
                log.info("事务订单已不是PENDING，跳过超时调度: orderNo={}, orderId={}, status={}",
                        orderNo, orderId, order.getStatus());
                return null;
            }

            // 重试必须恢复同一个 timeout messageId，不能每次随机生成另一个取消检查。
            OrderEventPayload payload = buildEventPayload(order, sortedItems(orderId));
            String timeoutMessageId = stableUuid("transaction-timeout:" + createdMessageId);
            RocketMessageEnvelope<JsonNode> timeoutEnvelope = messageCodec.newEnvelope(
                    timeoutMessageId,
                    RocketMqNames.EVENT_TRANSACTION_PAYMENT_TIMEOUT_CHECK,
                    orderNo,
                    payload);

            // 截止时间属于订单，不属于消费者。CREATE 积压后只能等待剩余时间，不能重新计时。
            LocalDateTime deadline = order.getCreatedAt()
                    .plus(Duration.ofMillis(properties.getOrderTimeoutMillis()));
            long remainingMillis = Math.max(0L, Duration.between(LocalDateTime.now(), deadline).toMillis());
            long delaySeconds = Math.max(
                    properties.getDelay().getMinimumBrokerDelaySeconds(),
                    (remainingMillis + 999L) / 1000L);
            return new TimeoutSchedulePlan(order.getOrderNo(), timeoutEnvelope, delaySeconds);
        });
        if (plan == null) {
            return;
        }

        // 第3步：同步 RocketMQ 调用明确位于只读检查事务与完成记录事务之间，不持有 PostgreSQL 事务。
        String brokerMessageId = publisher.publishDelay(
                properties.getTopics().getDelay(),
                properties.getTags().getTransactionTimeout(),
                plan.orderNo(),
                plan.delaySeconds(),
                plan.timeoutEnvelope());

        // 第4步：只有 Broker 明确返回成功后才记录完成。提交/回包不明确时 Broker 会重投 CREATE；
        // 下次可能再次发送相同稳定 ID，但绝不会因为一条提前提交的完成记录而永久漏发。
        transactionTemplate.executeWithoutResult(status -> {
            MqConsumedMessage consumed = new MqConsumedMessage();
            consumed.setConsumerName(consumerName);
            consumed.setMessageId(createdMessageId);
            consumed.setEventType(RocketMqNames.EVENT_ORDER_CREATED);
            consumed.setAggregateId(plan.orderNo());
            consumed.setConsumedAt(LocalDateTime.now());
            consumerRecordMapper.insertConsumedIfAbsent(consumed);
        });
        log.info("事务订单超时检查已安排: orderNo={}, timeoutMessageId={}, brokerMessageId={}, delaySeconds={}",
                orderNo, plan.timeoutEnvelope().getMessageId(), brokerMessageId, plan.delaySeconds());
    }

    /**
     * 延迟检查到期后取消仍为 PENDING 的订单。
     *
     * <p>这个方法不接收 timeoutMessageId：延迟消息只负责触发一次业务检查，CANCEL 自己生成新的事务 UUID。
     * 一次瞬时失败留下 ROLLED_BACK 后，Broker 重投延迟检查可用新 transactionId 再试；稳定防重维度是
     * CANCEL 操作和 orderNo，最终唯一赢家由订单 PENDING 条件更新决定。</p>
     */
    public void cancelExpiredOrder(String orderNo, Long orderId) {
        BusinessAssert.isTrue(orderNo != null && !orderNo.isBlank(), "事务超时消息的orderNo不能为空");
        BusinessAssert.isTrue(orderId != null && orderId > 0, "事务超时消息的orderId必须大于0");

        /*
         * 消息同时携带跨系统稳定键 orderNo 和本地数据库主键 orderId。必须按 orderNo 重读权威订单，
         * 再交叉校验 orderId；否则错误消息若把 A 的 orderNo 与 B 的 orderId 拼在一起，可能误取消 B。
         */
        PgOrder current = BusinessAssert.notNull(
                orderBusinessMapper.selectOrderByOrderNo(orderNo), "事务订单不存在");
        BusinessAssert.isTrue(orderId.equals(current.getId()), "事务超时消息的orderId与orderNo不属于同一订单");

        // 第1步：历史或错误路由不能让 Outbox 订单进入事务消息取消链。
        BusinessAssert.isTrue(transactionRecordRepository.isCommitted(
                        RocketMqNames.BUSINESS_ORDER, orderNo, RocketMqNames.OPERATION_CREATE),
                "该订单不是事务消息链创建，拒绝事务超时取消");
        if (!"PENDING".equals(current.getStatus())) {
            log.info("事务超时检查已过期，跳过取消: orderId={}, status={}", orderId, current.getStatus());
            return;
        }

        // 第2步：每次实际 CANCEL 尝试都生成新 UUID；ROLLED_BACK 后数据库部分唯一索引会释放重试资格。
        String transactionId = UUID.randomUUID().toString();
        OrderIdCommand command = new OrderIdCommand(orderId);
        RocketMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(
                transactionId, RocketMqNames.EVENT_ORDER_CANCELLED, orderNo, command);

        try {
            executeTransaction(
                    transactionId,
                    RocketMqNames.OPERATION_CANCEL,
                    orderNo,
                    properties.getTags().getOrderCancelled(),
                    envelope,
                    () -> transactionRecordRepository.prepare(
                            transactionId,
                            RocketMqNames.BUSINESS_ORDER,
                            orderNo,
                            RocketMqNames.OPERATION_CANCEL),
                    () -> cancelOrderLocally(orderId),
                    true);
        } catch (RuntimeException concurrentOrTransientFailure) {
            // 第3步：两个超时检查可能同时读到 PENDING。非 PENDING 说明支付或另一取消已经推进状态，
            // 当前延迟检查按幂等成功；仍是 PENDING 则可能只是瞬时故障，必须抛出让 Broker 后续重试。
            PgOrder latest = orderBusinessMapper.selectOrderByOrderNo(orderNo);
            if (latest != null && !"PENDING".equals(latest.getStatus())) {
                log.info("并发路径已推进订单，当前超时检查按幂等成功: orderId={}, status={}",
                        orderId, latest.getStatus());
                return;
            }
            throw concurrentOrTransientFailure;
        }
    }

    /**
     * CREATE、PAY、CANCEL 共用的可靠协调模板。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>第1步：调用 prepareAction，以 REQUIRES_NEW 独立提交 PREPARED；</li>
     *     <li>第2步：在数据库事务外发送半消息；发送失败时独立标记 ROLLED_BACK；</li>
     *     <li>第3步：TransactionTemplate 只包裹 localAction 和 PREPARED -> COMMITTED；</li>
     *     <li>第4步：本地失败后先读持久状态，再决定 commit、rollback 或保留半消息等待回查；</li>
     *     <li>第5步：本地成功后在数据库事务外提交半消息，RPC 不明确只告警并返回订单事实。</li>
     * </ol>
     */
    private OrderResponse executeTransaction(String transactionId,
                                             String operationType,
                                             String messageKey,
                                             String tag,
                                             RocketMessageEnvelope<JsonNode> envelope,
                                             Runnable prepareAction,
                                             Supplier<OrderResponse> localAction,
                                             boolean stateConflictIsSuccess) {
        BusinessAssert.isTrue(transactionId.equals(envelope.getMessageId()),
                "事务记录transactionId与信封messageId必须使用同一个UUID");
        BusinessAssert.isTrue(messageKey.equals(envelope.getAggregateId()),
                "RocketMQ消息Key与订单aggregateId必须表达同一个业务聚合");

        // 第1步：记录提交后即使进程崩溃，也有 Checker/清理任务可使用的持久依据。
        prepareAction.run();

        TransactionHandle half;
        try {
            // 第2步：半消息已到 Broker 但普通消费者不可见；这里没有持有数据库连接或业务行锁。
            half = publisher.beginTransaction(
                    properties.getTopics().getTransaction(), tag, messageKey, envelope);
        } catch (RuntimeException sendFailure) {
            markRolledBackAfterSendFailure(transactionId, sendFailure);
            throw sendFailure;
        }

        OrderResponse response;
        try {
            // 第3步：只有业务 SQL 和 COMMITTED 条件更新进入本地短事务。markCommitted 使用 MANDATORY，
            // 确保事务记录与订单/库存事实一起提交或回滚。
            response = transactionTemplate.execute(status -> {
                OrderResponse localResponse = BusinessAssert.notNull(localAction.get(), "本地订单事务没有返回结果");
                BusinessAssert.isTrue(
                        transactionRecordRepository.markCommitted(transactionId),
                        "事务记录已不再是PREPARED，拒绝提交重复或过期的" + operationType + "操作");
                return localResponse;
            });
            response = BusinessAssert.notNull(response, "本地订单事务没有返回结果");
        } catch (RuntimeException localFailure) {
            // TransactionTemplate 已经退出并完成回滚，随后 REQUIRES_NEW 状态查询/回滚不会挂在原事务上。
            return resolveLocalFailure(
                    transactionId, operationType, half, localFailure, stateConflictIsSuccess);
        }

        // 第5步：数据库已经明确提交。此时 commit RPC 异常不能反向撤销订单，只能交给 Broker 回查收敛。
        commitHalfOrWarn(half, transactionId, operationType);
        return response;
    }

    /**
     * 本地事务抛异常后的持久事实裁决。
     *
     * <p>不能看到 Java 异常就直接 rollback 半消息，因为数据库可能已经提交，只是提交响应丢失。必须先读取
     * 事务记录：COMMITTED 提交半消息；ROLLED_BACK 回滚半消息；PREPARED 才尝试条件标记明确回滚；
     * 查询失败或重读仍无终态时不猜测，让 Broker Checker 继续回查。</p>
     */
    private OrderResponse resolveLocalFailure(String transactionId,
                                              String operationType,
                                              TransactionHandle half,
                                              RuntimeException localFailure,
                                              boolean stateConflictIsSuccess) {
        MqTransactionRecord current;
        try {
            // 第1步：先读持久状态，优先识别“数据库实际已提交、调用线程却收到异常”的窗口。
            current = transactionRecordRepository.findById(transactionId);
        } catch (RuntimeException queryFailure) {
            localFailure.addSuppressed(queryFailure);
            throw localFailure;
        }

        if (current != null && "COMMITTED".equals(current.getStatus())) {
            // 第2步：数据库事实已经成功，必须提交而不是回滚半消息；RPC 再失败仍由 Checker 收敛。
            OrderResponse persisted = loadOrderResponse(current);
            commitHalfOrWarn(half, transactionId, operationType);
            return persisted;
        }

        if (current != null && "PREPARED".equals(current.getStatus())) {
            try {
                // 第3步：本地事务已明确回滚，只有抢到 PREPARED -> ROLLED_BACK 才拥有回滚半消息的依据。
                transactionRecordRepository.markRolledBack(transactionId, concise(localFailure));
                current = transactionRecordRepository.findById(transactionId);
            } catch (RuntimeException stateFailure) {
                localFailure.addSuppressed(stateFailure);
                throw localFailure;
            }
        }

        if (current != null && "COMMITTED".equals(current.getStatus())) {
            // 第4步：条件回滚0行可能是并发本地提交先赢，重读后仍必须尊重 COMMITTED。
            OrderResponse persisted = loadOrderResponse(current);
            commitHalfOrWarn(half, transactionId, operationType);
            return persisted;
        }
        if (current != null && "ROLLED_BACK".equals(current.getStatus())) {
            // 第5步：只有持久状态明确回滚，才向 Broker 回滚半消息。
            rollbackHalfMessage(half, localFailure);
            if (stateConflictIsSuccess && localFailure instanceof OrderStateConflictException) {
                PgOrder latest = findOrder(current);
                if (latest != null && !"PENDING".equals(latest.getStatus())) {
                    return OrderResponse.from(latest, sortedItems(latest.getId()));
                }
            }
            throw localFailure;
        }

        // 记录缺失、仍为 PREPARED 或未知状态都没有足够事实；不调用 commit/rollback，等待 Checker 继续裁决。
        throw localFailure;
    }

    /** CREATE 本地事务：从不可变命令恢复请求，复用完整下单 SQL，再读取真实订单响应。 */
    private OrderResponse createOrderLocally(CreateOrderCommand command) {
        BusinessAssert.isTrue(command.orderNo() != null && !command.orderNo().isBlank()
                        && command.userId() != null && command.userId() > 0
                        && command.items() != null && !command.items().isEmpty(),
                "CREATE命令缺少orderNo、userId或items");
        CompleteOrderCreateRequest request = new CompleteOrderCreateRequest();
        request.setOrderNo(command.orderNo());
        request.setUserId(command.userId());
        request.setItems(command.items().stream().map(item -> {
            CompleteOrderItemRequest target = new CompleteOrderItemRequest();
            target.setProductId(item.productId());
            target.setQuantity(item.quantity());
            return target;
        }).toList());

        CompleteOrderResponse created = pgMyBatisService.createCompleteOrder(request);
        return loadOrderResponse(created.getOrderId());
    }

    /** PAY 本地事务：PENDING 条件更新是支付与超时取消的唯一并发裁决点。 */
    private OrderResponse payOrderLocally(Long orderId) {
        PgOrder before = BusinessAssert.notNull(orderBusinessMapper.selectOrderById(orderId), "事务订单不存在");
        if (orderBusinessMapper.markPaid(orderId) != 1) {
            throw new OrderStateConflictException("支付失败：订单不是PENDING，当前状态=" + before.getStatus());
        }
        return loadOrderResponse(orderId);
    }

    /** CANCEL 本地事务：只有成功赢得 PENDING -> CANCELLED 的事务才允许恢复库存。 */
    private OrderResponse cancelOrderLocally(Long orderId) {
        PgOrder before = BusinessAssert.notNull(orderBusinessMapper.selectOrderById(orderId), "事务订单不存在");
        if (orderBusinessMapper.markCancelled(orderId) != 1) {
            throw new OrderStateConflictException("超时取消跳过：订单不是PENDING，当前状态=" + before.getStatus());
        }

        // 固定 productId 升序恢复库存，让并发取消尽量以相同顺序取得商品行锁，降低循环等待概率。
        List<PgOrderProduct> items = sortedItems(orderId);
        for (PgOrderProduct item : items) {
            BusinessAssert.isTrue(orderBusinessMapper.restoreStock(item.getProductId(), item.getQuantity()) == 1,
                    "恢复库存时商品不存在: " + item.getProductId());
        }
        PgOrder cancelled = BusinessAssert.notNull(
                orderBusinessMapper.selectOrderById(orderId), "取消后订单不存在");
        return OrderResponse.from(cancelled, items);
    }

    private OrderResponse loadOrderResponse(Long orderId) {
        PgOrder order = BusinessAssert.notNull(orderBusinessMapper.selectOrderById(orderId), "事务订单不存在");
        return OrderResponse.from(order, sortedItems(orderId));
    }

    /** 提交结果不明确时只能使用通用事务记录的 businessKey=orderNo 恢复订单事实。 */
    private OrderResponse loadOrderResponse(MqTransactionRecord record) {
        PgOrder order = BusinessAssert.notNull(findOrder(record), "已提交事务对应订单不存在");
        return OrderResponse.from(order, sortedItems(order.getId()));
    }

    private PgOrder findOrder(MqTransactionRecord record) {
        BusinessAssert.notNull(record, "事务记录不能为空");
        BusinessAssert.isTrue(RocketMqNames.BUSINESS_ORDER.equals(record.getBusinessType()),
                "事务记录不是订单业务类型");
        BusinessAssert.isTrue(record.getBusinessKey() != null && !record.getBusinessKey().isBlank(),
                "订单事务记录缺少businessKey/orderNo");
        return orderBusinessMapper.selectOrderByOrderNo(record.getBusinessKey());
    }

    private String requireOrderNo(PgOrder order) {
        BusinessAssert.notNull(order, "订单不能为空");
        BusinessAssert.isTrue(order.getOrderNo() != null && !order.getOrderNo().isBlank(), "订单缺少orderNo");
        return order.getOrderNo();
    }

    private List<PgOrderProduct> sortedItems(Long orderId) {
        return orderBusinessMapper.selectOrderProducts(orderId).stream()
                .sorted(Comparator.comparing(PgOrderProduct::getProductId))
                .toList();
    }

    private OrderEventPayload buildEventPayload(PgOrder order, List<PgOrderProduct> items) {
        OrderResponse response = OrderResponse.from(order, items);
        OrderEventPayload payload = new OrderEventPayload();
        payload.setOrderId(response.getOrderId());
        payload.setOrderNo(response.getOrderNo());
        payload.setUserId(order.getUserId());
        payload.setTotalAmount(response.getTotalAmount());
        payload.setItemCount(response.getItemCount());
        payload.setProductIds(items.stream().map(PgOrderProduct::getProductId).toList());
        return payload;
    }

    private void markRolledBackAfterSendFailure(String transactionId, RuntimeException sendFailure) {
        try {
            transactionRecordRepository.markRolledBack(
                    transactionId, "半消息发送失败: " + concise(sendFailure));
        } catch (RuntimeException stateFailure) {
            sendFailure.addSuppressed(stateFailure);
        }
    }

    private void commitHalfOrWarn(TransactionHandle half, String transactionId, String operationType) {
        try {
            half.commit();
        } catch (Exception commitFailure) {
            log.warn("数据库已提交但半消息commit结果不明确，等待Broker回查: transactionId={}, operationType={}",
                    transactionId, operationType, commitFailure);
        }
    }

    private void rollbackHalfMessage(TransactionHandle half, RuntimeException originalFailure) {
        try {
            half.rollback();
        } catch (Exception rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    private String stableUuid(String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String concise(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    /** 短只读事务生成的不可变发送计划；离开事务后不再访问懒加载对象或数据库状态。 */
    private record TimeoutSchedulePlan(String orderNo,
                                       RocketMessageEnvelope<JsonNode> timeoutEnvelope,
                                       long delaySeconds) {
    }
}
