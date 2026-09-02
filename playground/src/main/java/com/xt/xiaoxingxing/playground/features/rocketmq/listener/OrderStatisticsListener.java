package com.xt.xiaoxingxing.playground.features.rocketmq.listener;

import com.xt.xiaoxingxing.playground.features.rocketmq.constants.OrderMqConstants;
import com.xt.xiaoxingxing.playground.features.rocketmq.enums.OrderOperation;
import com.xt.xiaoxingxing.playground.features.rocketmq.service.OrderStatisticsService;
import com.xt.xiaoxingxing.playground.features.rocketmq.util.RocketMessageCodec;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** 订单统计消息监听器。 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        endpoints = "${rocketmq.producer.endpoints}",
        accessKey = "${rocketmq.producer.access-key:}",
        secretKey = "${rocketmq.producer.secret-key:}",
        namespace = "${rocketmq.producer.namespace:}",
        topic = OrderMqConstants.TOPIC_TRANSACTION,
        consumerGroup = OrderMqConstants.CONSUMER_GROUP_ORDER_STATISTICS,
        requestTimeout = 10,
        tag = OrderMqConstants.TAG_ORDER_CREATED + " || "
                + OrderMqConstants.TAG_ORDER_PAID + " || " + OrderMqConstants.TAG_ORDER_CANCELLED)
public class OrderStatisticsListener implements RocketMQListener {

    private final RocketMessageCodec messageCodec;
    private final OrderStatisticsService orderStatisticsService;

    /** 消费订单统计消息。 */
    @Override
    public ConsumeResult consume(MessageView messageView) {
        try {
            String tag = messageView.getTag()
                    .orElseThrow(() -> new BusinessException("RocketMQ消息缺少Tag"));
            String orderNo = messageView.getKeys().stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("RocketMQ消息缺少订单Key"));
            orderStatisticsService.record(
                    messageCodec.decodeTransactionId(messageView),
                    OrderOperation.fromTag(tag),
                    orderNo);
            return ConsumeResult.SUCCESS;
        } catch (Exception exception) {
            log.error("订单统计消息消费失败: brokerMessageId={}, deliveryAttempt={}",
                    messageView.getMessageId(), messageView.getDeliveryAttempt(), exception);
            return ConsumeResult.FAILURE;
        }
    }

}
