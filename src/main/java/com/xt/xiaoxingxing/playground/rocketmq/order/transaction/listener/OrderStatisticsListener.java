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
 * 事务消息订单统计 Listener。
 *
 * <p>CREATE、PAY、CANCEL 半消息携带的是不同命令 DTO，统计投影却只关心最终订单事实。因此本类不根据命令
 * payload 计算金额和件数，而是校验 COMMITTED 事务记录后重新读取 PostgreSQL 订单及明细。</p>
 */
@Component("transactionOrderStatisticsListener")
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@RocketMQMessageListener(
        endpoints = "${playground.rocketmq.endpoints}",
        accessKey = "${playground.rocketmq.consumer.access-key}",
        secretKey = "${playground.rocketmq.consumer.secret-key}",
        namespace = "${playground.rocketmq.consumer.namespace}",
        filterExpressionType = "${playground.rocketmq.consumer.filter-expression-type}",
        topic = "${playground.rocketmq.topics.transaction}",
        consumerGroup = "${playground.rocketmq.consumer-groups.transaction-order-statistics}",
        tag = "${playground.rocketmq.subscriptions.statistics-events}")
public class OrderStatisticsListener implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final TransactionRecordRepository transactionRecordRepository;
    private final MqOrderBusinessMapper orderBusinessMapper;
    private final OrderEventHandler orderEventHandler;
    private final RocketMqLearningProperties properties;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(
                messageView,
                properties.getConsumerGroups().getTransactionOrderStatistics(),
                this::handle);
    }

    private void handle(String actualTag, RocketMessageEnvelope<JsonNode> envelope) {
        // 第1步：统计组订阅三种事实；Tag、eventType 和事务 operationType 必须一一对应。
        String expectedOperation = expectedOperation(actualTag, envelope.getEventType());

        // 第2步：完全忽略事务命令 payload，从持久化事实恢复金额、件数和商品集合。
        OrderEventPayload payload = restoreCommittedFact(expectedOperation, envelope);

        // 第3步：消费幂等 INSERT 与统计 UPSERT 由共享 Handler 在同一个 PostgreSQL 事务完成。
        orderEventHandler.recordStatistics(
                properties.getConsumerGroups().getTransactionOrderStatistics(),
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
        } else if (properties.getTags().getOrderPaid().equals(actualTag)) {
            expectedEventType = RocketMqNames.EVENT_ORDER_PAID;
            operationType = RocketMqNames.OPERATION_PAY;
        } else if (properties.getTags().getOrderCancelled().equals(actualTag)) {
            expectedEventType = RocketMqNames.EVENT_ORDER_CANCELLED;
            operationType = RocketMqNames.OPERATION_CANCEL;
        } else {
            throw new MessageDecodingException("事务统计Listener不支持Tag: " + actualTag);
        }
        if (!expectedEventType.equals(eventType)) {
            throw new MessageDecodingException(
                    "事务统计消息Tag与eventType不匹配: tag=" + actualTag + ", eventType=" + eventType);
        }
        return operationType;
    }

    /** 只读恢复已提交订单事实，不执行订单写 SQL，也不信任消息命令快照中的业务字段。 */
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
