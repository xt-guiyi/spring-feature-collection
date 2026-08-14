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

/** 多消费组演示 A：审计组拥有独立消费进度，会收到 DEMO 消息的一份副本。 */
@Slf4j
@Component
@RocketMqLearningListener
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}", accessKey = "${playground.rocketmq.consumer.access-key}",
        secretKey = "${playground.rocketmq.consumer.secret-key}", namespace = "${playground.rocketmq.consumer.namespace}",
        filterExpressionType = "${playground.rocketmq.consumer.filter-expression-type}",
        topic = "${playground.rocketmq.topics.normal}", consumerGroup = "${playground.rocketmq.consumer-groups.broadcast-audit}",
        tag = "${playground.rocketmq.tags.demo}")
public class BroadcastAuditConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final RocketMqLearningProperties properties;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(messageView, properties.getConsumerGroups().getBroadcastAudit(),
                consumerSupport.demoRouteContract(),
                envelope -> log.info("多组审计副作用: businessMessageId={}, aggregateId={}",
                        envelope.getMessageId(), envelope.getAggregateId()));
    }
}
