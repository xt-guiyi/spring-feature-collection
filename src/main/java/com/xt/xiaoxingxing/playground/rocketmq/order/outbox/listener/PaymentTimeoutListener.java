package com.xt.xiaoxingxing.playground.rocketmq.order.outbox.listener;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderEventPayload;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.order.outbox.OrderService;
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
 * Outbox 付款超时检查 Listener。
 *
 * <p>延迟消息到达只表示“现在检查订单”，不代表无条件取消。Listener 取得真实 orderId 后调用业务
 * {@link OrderService#cancelExpiredOrder(Long)}；Service 会重新读取状态，并用 PENDING 条件更新与支付竞争。</p>
 */
@Component("outboxPaymentTimeoutListener")
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@RocketMQMessageListener(
        endpoints = "${playground.rocketmq.endpoints}",
        accessKey = "${playground.rocketmq.consumer.access-key}",
        secretKey = "${playground.rocketmq.consumer.secret-key}",
        namespace = "${playground.rocketmq.consumer.namespace}",
        filterExpressionType = "${playground.rocketmq.consumer.filter-expression-type}",
        topic = "${playground.rocketmq.topics.delay}",
        consumerGroup = "${playground.rocketmq.consumer-groups.outbox-order-timeout}",
        tag = "${playground.rocketmq.tags.outbox-timeout}")
public class PaymentTimeoutListener implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final OrderService orderService;
    private final RocketMqLearningProperties properties;
    private final JsonMapper jsonMapper;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(
                messageView,
                properties.getConsumerGroups().getOutboxOrderTimeout(),
                this::handle);
    }

    private void handle(String actualTag, RocketMessageEnvelope<JsonNode> envelope) {
        // 第1步：Outbox 与事务消息共用延迟 Topic，因此必须同时核对专用 Tag 和 eventType，防止两套链路串线。
        boolean routeMatches = properties.getTags().getOutboxTimeout().equals(actualTag)
                && RocketMqNames.EVENT_OUTBOX_PAYMENT_TIMEOUT_CHECK.equals(envelope.getEventType());
        if (!routeMatches) {
            throw new MessageDecodingException(
                    "Outbox超时消息Tag与eventType不匹配: tag=" + actualTag
                            + ", eventType=" + envelope.getEventType());
        }

        // 第2步：Listener 只把真实订单 ID 交给业务 Service，不传 Tag、MessageView 或原始信封。
        OrderEventPayload payload = decodePayload(envelope);
        orderService.cancelExpiredOrder(payload.getOrderId());
    }

    private OrderEventPayload decodePayload(RocketMessageEnvelope<JsonNode> envelope) {
        try {
            OrderEventPayload payload = jsonMapper.treeToValue(envelope.getPayload(), OrderEventPayload.class);
            boolean valid = payload != null
                    && payload.getOrderId() != null && payload.getOrderId() > 0
                    && String.valueOf(payload.getOrderId()).equals(envelope.getAggregateId());
            if (!valid) {
                throw new MessageDecodingException(
                        "Outbox超时消息缺少合法orderId，或aggregateId与orderId不一致");
            }
            return payload;
        } catch (JacksonException | ClassCastException exception) {
            throw new MessageDecodingException("Outbox超时消息payload格式不正确", exception);
        }
    }
}
