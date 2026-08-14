package com.xt.xiaoxingxing.playground.rocketmq.consumer.demo;

import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningListener;
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
@RocketMqLearningListener
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}", accessKey = "${playground.rocketmq.consumer.access-key}",
        secretKey = "${playground.rocketmq.consumer.secret-key}", namespace = "${playground.rocketmq.consumer.namespace}",
        filterExpressionType = "${playground.rocketmq.consumer.filter-expression-type}",
        topic = "${playground.rocketmq.topics.normal}", consumerGroup = "${playground.rocketmq.consumer-groups.broadcast-notification}",
        tag = "${playground.rocketmq.tags.demo}")
public class BroadcastNotificationConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final RocketMqLearningProperties properties;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(messageView, properties.getConsumerGroups().getBroadcastNotification(),
                consumerSupport.demoRouteContract(),
                envelope -> log.info("多组通知副作用: businessMessageId={}, eventType={}",
                        envelope.getMessageId(), envelope.getEventType()));
    }
}
