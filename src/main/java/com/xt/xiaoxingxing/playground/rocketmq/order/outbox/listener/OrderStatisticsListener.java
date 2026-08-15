package com.xt.xiaoxingxing.playground.rocketmq.order.outbox.listener;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderEventPayload;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.order.OrderEventHandler;
import com.xt.xiaoxingxing.playground.rocketmq.support.MessageDecodingException;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketConsumerSupport;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Outbox 订单统计 Listener；创建、支付和取消三个事实分别推进同一统计投影。 */
@Component("outboxOrderStatisticsListener")
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@RocketMQMessageListener(
        endpoints = "${playground.rocketmq.endpoints}",
        accessKey = "${playground.rocketmq.consumer.access-key}",
        secretKey = "${playground.rocketmq.consumer.secret-key}",
        namespace = "${playground.rocketmq.consumer.namespace}",
        filterExpressionType = "${playground.rocketmq.consumer.filter-expression-type}",
        topic = "${playground.rocketmq.topics.normal}",
        consumerGroup = "${playground.rocketmq.consumer-groups.outbox-order-statistics}",
        tag = "${playground.rocketmq.subscriptions.statistics-events}")
public class OrderStatisticsListener implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final OrderEventHandler orderEventHandler;
    private final RocketMqLearningProperties properties;
    private final JsonMapper jsonMapper;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(
                messageView,
                properties.getConsumerGroups().getOutboxOrderStatistics(),
                this::handle);
    }

    private void handle(String actualTag, RocketMessageEnvelope<JsonNode> envelope) {
        // 第1步：统计组比缓存组多接受 ORDER_PAID，但仍要求 Tag 与 eventType 一一对应。
        String expectedEventType;
        if (properties.getTags().getOrderCreated().equals(actualTag)) {
            expectedEventType = RocketMqNames.EVENT_ORDER_CREATED;
        } else if (properties.getTags().getOrderPaid().equals(actualTag)) {
            expectedEventType = RocketMqNames.EVENT_ORDER_PAID;
        } else if (properties.getTags().getOrderCancelled().equals(actualTag)) {
            expectedEventType = RocketMqNames.EVENT_ORDER_CANCELLED;
        } else {
            throw new MessageDecodingException("Outbox统计Listener不支持Tag: " + actualTag);
        }
        if (!expectedEventType.equals(envelope.getEventType())) {
            throw new MessageDecodingException(
                    "Outbox统计消息Tag与eventType不匹配: tag=" + actualTag
                            + ", eventType=" + envelope.getEventType());
        }

        // 第2步：解码完成后只传业务参数；统计事务与幂等由共享 Handler 统一保证。
        OrderEventPayload payload = decodePayload(envelope);
        orderEventHandler.recordStatistics(
                properties.getConsumerGroups().getOutboxOrderStatistics(),
                envelope.getMessageId(),
                envelope.getEventType(),
                payload);
    }

    private OrderEventPayload decodePayload(RocketMessageEnvelope<JsonNode> envelope) {
        try {
            OrderEventPayload payload = jsonMapper.treeToValue(envelope.getPayload(), OrderEventPayload.class);
            boolean valid = payload != null
                    && payload.getOrderId() != null && payload.getOrderId() > 0
                    && payload.getTotalAmount() != null && payload.getTotalAmount().signum() >= 0
                    && String.valueOf(payload.getOrderId()).equals(envelope.getAggregateId());
            if (!valid) {
                throw new MessageDecodingException(
                        "Outbox统计消息缺少合法orderId/totalAmount，或aggregateId与orderId不一致");
            }
            return payload;
        } catch (JacksonException | ClassCastException exception) {
            throw new MessageDecodingException("Outbox统计消息payload格式不正确", exception);
        }
    }
}
