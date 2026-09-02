package com.xt.xiaoxingxing.playground.features.rocketmq.listener;

import com.xt.xiaoxingxing.playground.features.rocketmq.constants.OrderMqConstants;
import com.xt.xiaoxingxing.playground.features.rocketmq.service.PaymentTimeoutScheduler;
import com.xt.xiaoxingxing.playground.features.rocketmq.util.RocketMessageCodec;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** 支付超时调度消息监听器。 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        endpoints = "${rocketmq.producer.endpoints}",
        accessKey = "${rocketmq.producer.access-key:}",
        secretKey = "${rocketmq.producer.secret-key:}",
        namespace = "${rocketmq.producer.namespace:}",
        topic = OrderMqConstants.TOPIC_TRANSACTION,
        consumerGroup = OrderMqConstants.CONSUMER_GROUP_TIMEOUT_SCHEDULER,
        requestTimeout = 10,
        tag = OrderMqConstants.TAG_ORDER_CREATED)
public class PaymentTimeoutScheduleListener implements RocketMQListener {

    private final RocketMessageCodec messageCodec;
    private final PaymentTimeoutScheduler paymentTimeoutScheduler;

    /** 消费订单创建消息并安排超时检查。 */
    @Override
    public ConsumeResult consume(MessageView messageView) {
        try {
            String orderNo = messageView.getKeys().stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("RocketMQ消息缺少订单Key"));
            paymentTimeoutScheduler.schedule(
                    orderNo, messageCodec.decodeTransactionId(messageView));
            return ConsumeResult.SUCCESS;
        } catch (Exception exception) {
            log.error("支付超时调度消息消费失败: brokerMessageId={}, deliveryAttempt={}",
                    messageView.getMessageId(), messageView.getDeliveryAttempt(), exception);
            return ConsumeResult.FAILURE;
        }
    }
}
