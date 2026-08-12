package com.xt.xiaoxingxing.playground.rocketmq.consumer.demo;

import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketConsumerSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** 多消费组演示 B：通知组和审计组各收一份；同组多个实例才是负载分担。 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}",
        topic = RocketMqNames.NORMAL_TOPIC, consumerGroup = RocketMqNames.BROADCAST_NOTIFICATION_GROUP,
        tag = RocketMqNames.TAG_DEMO, sslEnabled = false)
public class BroadcastNotificationConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(messageView, RocketMqNames.BROADCAST_NOTIFICATION_GROUP,
                RocketConsumerSupport.DEMO_ROUTE_CONTRACT,
                envelope -> log.info("多组通知副作用: businessMessageId={}, eventType={}",
                        envelope.getMessageId(), envelope.getEventType()));
    }
}
