package com.xt.xiaoxingxing.playground.rocketmq.order.transaction.listener;

import tools.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrder;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgOrderProduct;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.entity.MqTransactionRecord;
import com.xt.xiaoxingxing.playground.rocketmq.infrastructure.TransactionRecordRepository;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqOrderBusinessMapper;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderEventPayload;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.order.OrderEventHandler;
import com.xt.xiaoxingxing.playground.rocketmq.order.OrderResponse;
import com.xt.xiaoxingxing.playground.rocketmq.support.MessageDecodingException;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketConsumerSupport;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 事务消息订单的商品缓存失效 Listener。
 *
 * <p>事务半消息中的命令只说明“生产者准备执行什么”，不能直接当作已经提交的订单事实。本 Listener 会使用
 * {@code envelope.messageId} 查询 COMMITTED 事务记录，再从 PostgreSQL 重读订单和明细，最后才把真实负载
 * 交给共享 {@link OrderEventHandler} 删除缓存。</p>
 */
@Component("transactionProductCacheInvalidationListener")
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@RocketMQMessageListener(
        endpoints = "${playground.rocketmq.endpoints}",
        accessKey = "${playground.rocketmq.consumer.access-key}",
        secretKey = "${playground.rocketmq.consumer.secret-key}",
        namespace = "${playground.rocketmq.consumer.namespace}",
        filterExpressionType = "${playground.rocketmq.consumer.filter-expression-type}",
        topic = "${playground.rocketmq.topics.transaction}",
        consumerGroup = "${playground.rocketmq.consumer-groups.transaction-order-cache}",
        tag = "${playground.rocketmq.subscriptions.cache-events}")
public class ProductCacheInvalidationListener implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final TransactionRecordRepository transactionRecordRepository;
    private final MqOrderBusinessMapper orderBusinessMapper;
    private final OrderEventHandler orderEventHandler;
    private final RocketMqLearningProperties properties;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(
                messageView,
                properties.getConsumerGroups().getTransactionOrderCache(),
                this::handle);
    }

    private void handle(String actualTag, RocketMessageEnvelope<JsonNode> envelope) {
        /*
         * 第1步：缓存组只订阅扣库存和恢复库存事件，因此只接受 CREATED/CANCELLED。
         * Broker Tag 过滤是第一道边界，这里的二次校验用于防止错误生产者把 Tag 与 eventType 拼错。
         */
        String expectedOperation = expectedOperation(actualTag, envelope.getEventType());

        // 第2步：不解析 envelope.payload。它是事务命令快照，不是消费者可直接采用的最终订单事实。
        OrderEventPayload payload = restoreCommittedFact(expectedOperation, envelope);

        // 第3步：Listener 只传纯业务参数；Redis 删除顺序与消费幂等由共享 Handler 统一保证。
        orderEventHandler.invalidateProductCache(
                properties.getConsumerGroups().getTransactionOrderCache(),
                envelope.getMessageId(),
                envelope.getEventType(),
                payload);
    }

    private String expectedOperation(String actualTag, String eventType) {
        String expectedEventType;
        String operationType;
        if (properties.getTags().getOrderCreated().equals(actualTag)) {
            expectedEventType = RocketMqNames.EVENT_ORDER_CREATED;
            operationType = RocketMqNames.OPERATION_CREATE;
        } else if (properties.getTags().getOrderCancelled().equals(actualTag)) {
            expectedEventType = RocketMqNames.EVENT_ORDER_CANCELLED;
            operationType = RocketMqNames.OPERATION_CANCEL;
        } else {
            throw new MessageDecodingException("事务缓存Listener不支持Tag: " + actualTag);
        }
        if (!expectedEventType.equals(eventType)) {
            throw new MessageDecodingException(
                    "事务缓存消息Tag与eventType不匹配: tag=" + actualTag + ", eventType=" + eventType);
        }
        return operationType;
    }

    /**
     * 从事务记录和订单表恢复已提交事实。
     *
     * <p>这里的 SQL 全是只读查询；Listener 不更新订单、不恢复库存，也不持有业务事务。任一事实不一致都会抛出
     * 异常，由 {@link RocketConsumerSupport} 返回 FAILURE，避免错误消息被确认后静默丢失。</p>
     */
    private OrderEventPayload restoreCommittedFact(String expectedOperation,
                                                   RocketMessageEnvelope<JsonNode> envelope) {
        MqTransactionRecord record = BusinessAssert.notNull(
                transactionRecordRepository.findById(envelope.getMessageId()), "事务记录不存在");
        BusinessAssert.isTrue(envelope.getMessageId().equals(record.getTransactionId())
                        && "COMMITTED".equals(record.getStatus())
                        && expectedOperation.equals(record.getOperationType())
                        && RocketMqNames.BUSINESS_ORDER.equals(record.getBusinessType())
                        && record.getBusinessKey() != null
                        && !record.getBusinessKey().isBlank(),
                "事务消息与已提交记录不一致");

        BusinessAssert.isTrue(record.getBusinessKey().equals(envelope.getAggregateId()),
                "事务消息aggregateId与事务记录businessKey不一致");

        PgOrder order = BusinessAssert.notNull(
                orderBusinessMapper.selectOrderByOrderNo(record.getBusinessKey()), "已提交事务对应订单不存在");
        BusinessAssert.isTrue(order.getOrderNo() != null
                        && record.getBusinessKey().equals(order.getOrderNo()),
                "事务记录与订单事实不一致");

        List<PgOrderProduct> items = orderBusinessMapper.selectOrderProducts(order.getId()).stream()
                .sorted(Comparator.comparing(PgOrderProduct::getProductId))
                .toList();
        BusinessAssert.isTrue(!items.isEmpty(), "已提交订单缺少明细");
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
}
