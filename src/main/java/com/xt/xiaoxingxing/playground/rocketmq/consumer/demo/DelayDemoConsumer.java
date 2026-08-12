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

/** 自定义延迟演示消费者，打印期望投递时间与实际接收时间的偏差。 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}",
        topic = RocketMqNames.DELAY_TOPIC, consumerGroup = RocketMqNames.DELAY_DEMO_GROUP,
        tag = RocketMqNames.TAG_DEMO, sslEnabled = false)
public class DelayDemoConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final ObjectMapper objectMapper;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(messageView, RocketMqNames.DELAY_DEMO_GROUP,
                RocketConsumerSupport.DEMO_ROUTE_CONTRACT, envelope -> {
            DemoMessagePayload payload = objectMapper.treeToValue(envelope.getPayload(), DemoMessagePayload.class);
            long expectedAt = Long.parseLong(payload.getBusinessKey());
            long receivedAt = System.currentTimeMillis();
            log.info("延迟消息: expectedAt={}, brokerDeliveryAt={}, receivedAt={}, driftMillis={}, text={}",
                    expectedAt, messageView.getDeliveryTimestamp().orElse(null), receivedAt,
                    receivedAt - expectedAt, payload.getText());
        });
    }
}
