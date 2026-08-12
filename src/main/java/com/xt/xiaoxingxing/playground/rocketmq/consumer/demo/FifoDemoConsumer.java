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

/** FIFO 消费日志用于观察同一 MessageGroup 内 sequence 递增，而非全 Topic 全局有序。 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}",
        topic = RocketMqNames.FIFO_TOPIC, consumerGroup = RocketMqNames.FIFO_DEMO_GROUP,
        tag = RocketMqNames.TAG_DEMO, sslEnabled = false)
public class FifoDemoConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final ObjectMapper objectMapper;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(messageView, RocketMqNames.FIFO_DEMO_GROUP,
                RocketConsumerSupport.DEMO_ROUTE_CONTRACT, envelope -> {
            DemoMessagePayload payload = objectMapper.treeToValue(envelope.getPayload(), DemoMessagePayload.class);
            log.info("FIFO消息: messageGroup={}, businessKey={}, sequence={}, businessMessageId={}",
                    messageView.getMessageGroup().orElse(null), payload.getBusinessKey(),
                    payload.getSequence(), envelope.getMessageId());
        });
    }
}
