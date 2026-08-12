package com.xt.xiaoxingxing.playground.rocketmq.consumer.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.message.DemoMessagePayload;
import com.xt.xiaoxingxing.playground.rocketmq.support.RocketConsumerSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.annotation.RocketMQMessageListener;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** 普通消息与 Tag 过滤消费者；只有 DEMO Tag 会进入本组。 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}",
        topic = RocketMqNames.NORMAL_TOPIC, consumerGroup = RocketMqNames.NORMAL_DEMO_GROUP,
        tag = RocketMqNames.TAG_DEMO, sslEnabled = false)
public class NormalTagConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final ObjectMapper objectMapper;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(messageView, RocketMqNames.NORMAL_DEMO_GROUP,
                RocketConsumerSupport.DEMO_ROUTE_CONTRACT, envelope -> {
            DemoMessagePayload payload = objectMapper.treeToValue(envelope.getPayload(), DemoMessagePayload.class);
            log.info("普通/Tag消费者收到消息: tag={}, text={}, businessMessageId={}",
                    messageView.getTag().orElse(null), payload.getText(), envelope.getMessageId());
        });
    }
}
