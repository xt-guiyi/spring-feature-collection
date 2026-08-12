package com.xt.xiaoxingxing.playground.rocketmq.consumer.order;

import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.service.RocketOrderConsumerService;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketConsumerSupport;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** 模拟通知组；只有业务事务完成后公共模板才向 Broker 返回 SUCCESS。 */
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}",
        topic = RocketMqNames.NORMAL_TOPIC, consumerGroup = RocketMqNames.ORDER_NOTIFICATION_GROUP,
        tag = RocketMqNames.TAG_ORDER_CREATED + "||" + RocketMqNames.TAG_ORDER_PAID + "||"
                + RocketMqNames.TAG_ORDER_CANCELLED, sslEnabled = false)
public class OrderNotificationConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final RocketOrderConsumerService consumerService;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(
                messageView, RocketMqNames.ORDER_NOTIFICATION_GROUP,
                RocketConsumerSupport.ORDER_EVENT_ROUTE_CONTRACT, consumerService::handleNotification);
    }
}
