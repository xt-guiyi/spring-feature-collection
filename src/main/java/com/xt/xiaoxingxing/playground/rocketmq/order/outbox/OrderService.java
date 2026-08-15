package com.xt.xiaoxingxing.playground.rocketmq.order.outbox;

import tools.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.postgresql.dto.request.CompleteOrderCreateRequest;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import com.xt.xiaoxingxing.playground.postgresql.service.PgMyBatisService;
import com.xt.xiaoxingxing.playground.postgresql.vo.CompleteOrderResponse;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqOutboxEvent;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqOrderBusinessMapper;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqOutboxEventMapper;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqTransactionRecordMapper;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderEventPayload;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.order.OrderResponse;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketMessageCodec;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Transactional Outbox 版订单业务服务。
 *
 * <p>本类只处理订单生命周期，不负责轮询或发送 RocketMQ。订单、订单明细、库存和 Outbox 都使用
 * {@code playgroundTransactionManager} 写入同一个 PostgreSQL 本地事务；事务提交以后，独立的
 * {@code OutboxRelay} 才会把消息发送到 Broker。这样订单接口不会出现“数据库已经成功，但网络发送失败后
 * 消息永久丢失”的双写窗口。</p>
 */
@Service("outboxOrderService")
public class OrderService {

    private final PgMyBatisService pgMyBatisService;
    private final MqOrderBusinessMapper orderBusinessMapper;
    private final MqTransactionRecordMapper transactionRecordMapper;
    private final MqOutboxEventMapper outboxEventMapper;
    private final RocketMessageCodec messageCodec;
    private final RocketMqLearningProperties properties;

    public OrderService(PgMyBatisService pgMyBatisService,
                        MqOrderBusinessMapper orderBusinessMapper,
                        MqTransactionRecordMapper transactionRecordMapper,
                        MqOutboxEventMapper outboxEventMapper,
                        RocketMessageCodec messageCodec,
                        RocketMqLearningProperties properties) {
        this.pgMyBatisService = pgMyBatisService;
        this.orderBusinessMapper = orderBusinessMapper;
        this.transactionRecordMapper = transactionRecordMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.messageCodec = messageCodec;
        this.properties = properties;
    }

    /**
     * 创建订单并登记两条必须发布的 Outbox。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>创建订单、明细，并按商品 ID 固定顺序执行带库存条件的扣减；</li>
     *     <li>重新读取订单和明细，以 PostgreSQL 中的成交事实构造响应及消息快照；</li>
     *     <li>写入立即发布的 {@code ORDER_CREATED} Outbox；</li>
     *     <li>写入未来投递的付款超时检查 Outbox；</li>
     *     <li>由同一个本地事务统一提交订单、库存与两条消息意图，任一步失败则全部回滚。</li>
     * </ol>
     *
     * <p>本方法不会调用 RocketMQ。HTTP 返回只代表数据库事实与消息意图已经可靠落库，不代表 Broker
     * 或任何消费者已经完成处理。</p>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public OrderResponse createOrder(CompleteOrderCreateRequest request) {
        // 第1步：复用现有完整下单事务。Spring 默认 REQUIRED 传播会让内部方法加入当前外层事务，
        // 因而订单头、明细、库存扣减以及后续 Outbox 插入最终只会一起提交或一起回滚。
        CompleteOrderResponse created = pgMyBatisService.createCompleteOrder(request);

        // 第2步：不能直接使用请求中的价格、数量汇总或商品顺序拼消息；重新读取落库事实后，
        // HTTP 响应、ORDER_CREATED 和超时检查三者才能描述同一份订单快照。
        PgOrder order = BusinessAssert.notNull(
                orderBusinessMapper.selectOrderById(created.getOrderId()), "Outbox订单创建后无法读取");
        List<PgOrderProduct> items = sortedItems(order.getId());
        OrderEventPayload payload = buildPayload(order, items);

        // 第3步：普通订单事实由缓存失效和统计两个不同消费组分别处理。
        appendEvent(
                String.valueOf(order.getId()),
                RocketMqNames.EVENT_ORDER_CREATED,
                properties.getTopics().getNormal(),
                properties.getTags().getOrderCreated(),
                order.getOrderNo(),
                null,
                payload);

        /*
         * 第4步：延迟消息表达“到期后重新检查”，而不是现在就决定未来一定取消。
         * 截止时间从订单的持久化 createdAt 计算，不从本段代码执行到此处的时刻重新开始倒计时。
         */
        LocalDateTime timeoutAt = order.getCreatedAt()
                .plus(Duration.ofMillis(properties.getOrderTimeoutMillis()));
        appendEvent(
                String.valueOf(order.getId()),
                RocketMqNames.EVENT_OUTBOX_PAYMENT_TIMEOUT_CHECK,
                properties.getTopics().getDelay(),
                properties.getTags().getOutboxTimeout(),
                order.getOrderNo(),
                timeoutAt,
                payload);

        // 第5步：响应只返回订单业务事实，不向调用者暴露内部事件 ID 或 Broker 状态。
        return OrderResponse.from(order, items);
    }

    /**
     * 支付 Outbox 订单。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>读取订单并确认它不属于 RocketMQ 事务消息订单链；</li>
     *     <li>使用 {@code WHERE status='PENDING'} 条件更新与超时取消竞争；</li>
     *     <li>更新成功后读取 PAID 事实和明细，并在同一事务写入 {@code ORDER_PAID} Outbox；</li>
     *     <li>重复支付若发现订单已经 PAID，直接返回既有事实，不重复写消息。</li>
     * </ol>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public OrderResponse payOrder(Long orderId) {
        // 第1步：首次 SELECT 只用于区分订单来源和改善错误信息；它不是并发裁决依据。
        PgOrder before = BusinessAssert.notNull(orderBusinessMapper.selectOrderById(orderId), "订单不存在");
        BusinessAssert.isTrue(transactionRecordMapper.countCommitted(
                        RocketMqNames.BUSINESS_ORDER,
                        before.getOrderNo(),
                        RocketMqNames.OPERATION_CREATE) == 0,
                "该订单由RocketMQ事务消息方案创建，请使用事务消息支付入口");

        /*
         * 第2步：真正的并发裁决必须放在同一条 UPDATE 中。支付和超时取消并发时，数据库只允许
         * 一方把 PENDING 推进到终态；另一方得到 0 行后必须重新读取，绝不能继续写事件或恢复库存。
         */
        if (orderBusinessMapper.markPaid(orderId) != 1) {
            PgOrder latest = BusinessAssert.notNull(orderBusinessMapper.selectOrderById(orderId), "订单不存在");
            if ("PAID".equals(latest.getStatus())) {
                // 重复请求已经由第一次支付完成。返回当前事实即可，不能再追加第二条 ORDER_PAID。
                return OrderResponse.from(latest, sortedItems(orderId));
            }
            BusinessAssert.isTrue(false,
                    "支付失败：订单不是PENDING，当前状态=" + latest.getStatus()
                            + "，读取时状态=" + before.getStatus());
        }

        // 第3步：事件和响应都从更新后的数据库事实构造；Outbox 插入失败会让 PAID 更新一起回滚。
        PgOrder paid = BusinessAssert.notNull(orderBusinessMapper.selectOrderById(orderId), "支付后订单不存在");
        List<PgOrderProduct> items = sortedItems(orderId);
        appendEvent(
                String.valueOf(orderId),
                RocketMqNames.EVENT_ORDER_PAID,
                properties.getTopics().getNormal(),
                properties.getTags().getOrderPaid(),
                paid.getOrderNo(),
                null,
                buildPayload(paid, items));
        return OrderResponse.from(paid, items);
    }

    /**
     * 付款超时后尝试取消订单。
     *
     * <p>实现步骤：</p>
     * <ol>
     *     <li>重新读取订单并确认属于 Outbox 订单链；</li>
     *     <li>使用 {@code PENDING -> CANCELLED} 条件更新与支付竞争；</li>
     *     <li>只有赢得状态更新的事务才按 productId 固定顺序恢复库存；</li>
     *     <li>重新读取 CANCELLED 事实并在同一事务写入 {@code ORDER_CANCELLED} Outbox；</li>
     *     <li>订单已支付、已取消或不存在时安全返回，不重复恢复库存和写事件。</li>
     * </ol>
     */
    @Transactional(transactionManager = "playgroundTransactionManager", rollbackFor = Exception.class)
    public void cancelExpiredOrder(Long orderId) {
        // 第1步：延迟消息只是“现在检查一次”。历史订单可能已清理，或者用户已经完成支付，
        // 因此不能把消息到期等同于无条件取消。
        PgOrder before = orderBusinessMapper.selectOrderById(orderId);
        if (before == null) {
            return;
        }
        BusinessAssert.isTrue(transactionRecordMapper.countCommitted(
                        RocketMqNames.BUSINESS_ORDER,
                        before.getOrderNo(),
                        RocketMqNames.OPERATION_CREATE) == 0,
                "Outbox超时消息不能取消RocketMQ事务消息方案创建的订单");

        // 第2步：0 行表示支付、先前取消或其他并发事务已经推进状态；此时安全返回。
        if (orderBusinessMapper.markCancelled(orderId) != 1) {
            return;
        }

        // 第3步：只有赢得 PENDING -> CANCELLED 的事务能到达这里。固定商品加锁顺序可降低
        // 多个订单同时恢复相同商品库存时反向获取行锁造成死锁的概率。
        List<PgOrderProduct> items = sortedItems(orderId);
        for (PgOrderProduct item : items) {
            BusinessAssert.isTrue(orderBusinessMapper.restoreStock(item.getProductId(), item.getQuantity()) == 1,
                    "恢复库存时商品不存在: " + item.getProductId());
        }

        // 第4步：取消、库存恢复和 Outbox 属于同一事务；事件写入失败会让前两项全部回滚。
        PgOrder cancelled = BusinessAssert.notNull(
                orderBusinessMapper.selectOrderById(orderId), "取消后订单不存在");
        appendEvent(
                String.valueOf(orderId),
                RocketMqNames.EVENT_ORDER_CANCELLED,
                properties.getTopics().getNormal(),
                properties.getTags().getOrderCancelled(),
                cancelled.getOrderNo(),
                null,
                buildPayload(cancelled, items));
    }

    /**
     * 在当前订单事务中插入一条 Outbox；该方法故意保持 private，避免形成只转发 Mapper 的技术 Service。
     */
    private void appendEvent(String aggregateId,
                             String eventType,
                             String topic,
                             String tag,
                             String messageKey,
                             LocalDateTime deliverAt,
                             Object payload) {
        // 第1步：业务 messageId 在首次落库前生成，Relay 的所有发布重试都复用同一信封和同一 ID。
        RocketMessageEnvelope<JsonNode> envelope = messageCodec.newEnvelope(eventType, aggregateId, payload);

        // 第2步：Outbox 保存完整信封及 Broker 寻址信息，但此处只做数据库 INSERT，不进行网络调用。
        MqOutboxEvent event = new MqOutboxEvent();
        event.setId(envelope.getMessageId());
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setTopicName(topic);
        event.setMessageTag(tag);
        event.setMessageKey(messageKey);
        event.setDeliverAt(deliverAt);
        event.setPayload(messageCodec.toJson(envelope));
        event.setNextRetryAt(LocalDateTime.now());
        event.setCreatedAt(envelope.getOccurredAt());

        // 第3步：插入失败直接抛异常，让外层订单事务整体回滚，不能单独吞掉消息意图写入失败。
        BusinessAssert.isTrue(outboxEventMapper.insert(event) == 1, "Outbox事件写入失败");
    }

    /** Mapper 已按 product_id 排序，这里再次显式排序，让 Service 的锁顺序契约不依赖 SQL 实现细节。 */
    private List<PgOrderProduct> sortedItems(Long orderId) {
        return orderBusinessMapper.selectOrderProducts(orderId).stream()
                .sorted(Comparator.comparing(PgOrderProduct::getProductId))
                .toList();
    }

    /** 订单事件只描述已经落库的最终事实，不使用 HTTP 请求中的数量、金额或商品顺序。 */
    private OrderEventPayload buildPayload(PgOrder order, List<PgOrderProduct> items) {
        long itemCount = items.stream()
                .map(PgOrderProduct::getQuantity)
                .mapToLong(Integer::longValue)
                .sum();
        BusinessAssert.isTrue(itemCount <= Integer.MAX_VALUE, "订单商品总数过大");

        OrderEventPayload payload = new OrderEventPayload();
        payload.setOrderId(order.getId());
        payload.setOrderNo(order.getOrderNo());
        payload.setUserId(order.getUserId());
        payload.setTotalAmount(order.getTotalAmount());
        payload.setItemCount((int) itemCount);
        payload.setProductIds(items.stream().map(PgOrderProduct::getProductId).toList());
        return payload;
    }
}
