package com.xt.xiaoxingxing.playground.rocketmq.order.transaction.listener;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.message.OrderEventPayload;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import com.xt.xiaoxingxing.playground.rocketmq.order.transaction.OrderService;
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
 * 事务消息订单的付款超时执行 Listener。
 *
 * <p>延迟消息到达只表示“现在重新检查订单”，不是无条件取消指令。Listener 解出 orderNo 和 orderId，事务版
 * {@link OrderService#cancelExpiredOrder(String, Long)} 会同时核对 orderNo 与 orderId，再用 PENDING 条件更新与支付竞争。</p>
 */
@Component("transactionPaymentTimeoutListener")
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@RocketMQMessageListener(
        endpoints = "${playground.rocketmq.endpoints}",
        accessKey = "${playground.rocketmq.consumer.access-key}",
        secretKey = "${playground.rocketmq.consumer.secret-key}",
        namespace = "${playground.rocketmq.consumer.namespace}",
        filterExpressionType = "${playground.rocketmq.consumer.filter-expression-type}",
        topic = "${playground.rocketmq.topics.delay}",
        consumerGroup = "${playground.rocketmq.consumer-groups.transaction-order-timeout}",
        tag = "${playground.rocketmq.tags.transaction-timeout}")
public class PaymentTimeoutListener implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final JsonMapper jsonMapper;
    private final OrderService orderService;
    private final RocketMqLearningProperties properties;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(
                messageView,
                properties.getConsumerGroups().getTransactionOrderTimeout(),
                this::handle);
    }

    private void handle(String actualTag, RocketMessageEnvelope<JsonNode> envelope) {
        // 第1步：Outbox 与事务方案共用 DELAY Topic，专用 Tag + eventType 二次校验防止两套取消链串线。
        if (!properties.getTags().getTransactionTimeout().equals(actualTag)
                || !RocketMqNames.EVENT_TRANSACTION_PAYMENT_TIMEOUT_CHECK.equals(envelope.getEventType())) {
            throw new MessageDecodingException(
                    "事务超时消息Tag与eventType不匹配: tag=" + actualTag
                            + ", eventType=" + envelope.getEventType());
        }

        // 第2步：延迟事件 payload 是已提交订单事实快照；orderNo 是跨系统稳定业务键，orderId 用于本地定位与交叉校验。
        TimeoutOrderIdentity order = decodeOrderIdentity(envelope);

        // 第3步：Listener 不写订单、不恢复库存；全部状态竞争留给业务 Service 的本地事务。
        orderService.cancelExpiredOrder(order.orderNo(), order.orderId());
    }

    private TimeoutOrderIdentity decodeOrderIdentity(RocketMessageEnvelope<JsonNode> envelope) {
        try {
            OrderEventPayload payload = jsonMapper.treeToValue(envelope.getPayload(), OrderEventPayload.class);
            boolean valid = payload != null
                    && payload.getOrderId() != null
                    && payload.getOrderId() > 0
                    && payload.getOrderNo() != null
                    && !payload.getOrderNo().isBlank()
                    && payload.getOrderNo().equals(envelope.getAggregateId());
            if (!valid) {
                throw new MessageDecodingException(
                        "事务超时消息缺少合法orderId/orderNo，或aggregateId与payload.orderNo不一致");
            }
            return new TimeoutOrderIdentity(payload.getOrderNo(), payload.getOrderId());
        } catch (JacksonException | ClassCastException exception) {
            throw new MessageDecodingException("事务超时消息payload格式不正确", exception);
        }
    }

    /** 延迟消息中用于交叉校验同一订单的稳定业务键和本地主键。 */
    private record TimeoutOrderIdentity(String orderNo, Long orderId) {
    }
}
