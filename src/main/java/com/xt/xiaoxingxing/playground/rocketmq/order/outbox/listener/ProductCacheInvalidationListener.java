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

/**
 * Outbox 订单商品缓存失效 Listener。
 *
 * <p>Listener 只承担消息边界职责：解码信封、核对真实 Tag 与 eventType、把业务参数交给共享 Handler。
 * 缓存删除顺序、Redis 异常和消费幂等都不在 Listener 中重复实现。</p>
 */
@Component("outboxProductCacheInvalidationListener")
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@RocketMQMessageListener(
        endpoints = "${playground.rocketmq.endpoints}",
        accessKey = "${playground.rocketmq.consumer.access-key}",
        secretKey = "${playground.rocketmq.consumer.secret-key}",
        namespace = "${playground.rocketmq.consumer.namespace}",
        filterExpressionType = "${playground.rocketmq.consumer.filter-expression-type}",
        topic = "${playground.rocketmq.topics.normal}",
        consumerGroup = "${playground.rocketmq.consumer-groups.outbox-order-cache}",
        tag = "${playground.rocketmq.subscriptions.cache-events}")
public class ProductCacheInvalidationListener implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final OrderEventHandler orderEventHandler;
    private final RocketMqLearningProperties properties;
    private final JsonMapper jsonMapper;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(
                messageView,
                properties.getConsumerGroups().getOutboxOrderCache(),
                this::handle);
    }

    private void handle(String actualTag, RocketMessageEnvelope<JsonNode> envelope) {
        // 第1步：Broker 的 Tag 过滤是第一道边界；这里再次校验 Tag 和信封 eventType，防止协议错配误删缓存。
        String expectedEventType;
        if (properties.getTags().getOrderCreated().equals(actualTag)) {
            expectedEventType = RocketMqNames.EVENT_ORDER_CREATED;
        } else if (properties.getTags().getOrderCancelled().equals(actualTag)) {
            expectedEventType = RocketMqNames.EVENT_ORDER_CANCELLED;
        } else {
            throw new MessageDecodingException("Outbox缓存Listener不支持Tag: " + actualTag);
        }
        if (!expectedEventType.equals(envelope.getEventType())) {
            throw new MessageDecodingException(
                    "Outbox缓存消息Tag与eventType不匹配: tag=" + actualTag
                            + ", eventType=" + envelope.getEventType());
        }

        // 第2步：Listener 完成 JSON 到业务负载的转换，Handler 不依赖 JsonNode 或消息信封。
        OrderEventPayload payload = decodePayload(envelope);
        orderEventHandler.invalidateProductCache(
                properties.getConsumerGroups().getOutboxOrderCache(),
                envelope.getMessageId(),
                envelope.getEventType(),
                payload);
    }

    private OrderEventPayload decodePayload(RocketMessageEnvelope<JsonNode> envelope) {
        try {
            OrderEventPayload payload = jsonMapper.treeToValue(envelope.getPayload(), OrderEventPayload.class);
            boolean valid = payload != null
                    && payload.getOrderId() != null && payload.getOrderId() > 0
                    && payload.getProductIds() != null && !payload.getProductIds().isEmpty()
                    && payload.getProductIds().stream().allMatch(id -> id != null && id > 0)
                    && String.valueOf(payload.getOrderId()).equals(envelope.getAggregateId());
            if (!valid) {
                throw new MessageDecodingException(
                        "Outbox缓存消息缺少合法orderId/productIds，或aggregateId与orderId不一致");
            }
            return payload;
        } catch (JacksonException | ClassCastException exception) {
            throw new MessageDecodingException("Outbox缓存消息payload格式不正确", exception);
        }
    }
}
