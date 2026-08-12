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

/** 多消费组演示 A：审计组拥有独立消费进度，会收到 DEMO 消息的一份副本。 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}",
        topic = RocketMqNames.NORMAL_TOPIC, consumerGroup = RocketMqNames.BROADCAST_AUDIT_GROUP,
        tag = RocketMqNames.TAG_DEMO, sslEnabled = false)
public class BroadcastAuditConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(messageView, RocketMqNames.BROADCAST_AUDIT_GROUP,
                RocketConsumerSupport.DEMO_ROUTE_CONTRACT,
                envelope -> log.info("多组审计副作用: businessMessageId={}, aggregateId={}",
                        envelope.getMessageId(), envelope.getAggregateId()));
    }
}
