package com.xt.xiaoxingxing.playground.rocketmq.listener;

import com.xt.xiaoxingxing.playground.rocketmq.config.OrderMqConstants;
import com.xt.xiaoxingxing.playground.rocketmq.service.OrderService;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** 支付超时消息监听器。 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        endpoints = "${rocketmq.producer.endpoints}",
        accessKey = "${rocketmq.producer.access-key:}",
        secretKey = "${rocketmq.producer.secret-key:}",
        namespace = "${rocketmq.producer.namespace:}",
        topic = OrderMqConstants.TOPIC_DELAY,
        consumerGroup = OrderMqConstants.CONSUMER_GROUP_ORDER_TIMEOUT,
        tag = OrderMqConstants.TAG_PAYMENT_TIMEOUT_CHECK)
public class PaymentTimeoutListener implements RocketMQListener {

    private final OrderService orderService;

    /** 消费支付超时消息。 */
    @Override
    public ConsumeResult consume(MessageView messageView) {
        try {
            String orderNo = messageView.getKeys().stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("RocketMQ消息缺少订单Key"));
            orderService.cancelExpiredOrder(orderNo);
            return ConsumeResult.SUCCESS;
        } catch (Exception exception) {
            log.error("支付超时消息消费失败: brokerMessageId={}, deliveryAttempt={}",
                    messageView.getMessageId(), messageView.getDeliveryAttempt(), exception);
            return ConsumeResult.FAILURE;
        }
    }
}
