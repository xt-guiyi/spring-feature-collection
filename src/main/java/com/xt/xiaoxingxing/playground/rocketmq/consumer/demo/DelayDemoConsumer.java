package com.xt.xiaoxingxing.playground.rocketmq.consumer.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningListener;
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
@RocketMqLearningListener
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}", accessKey = "${playground.rocketmq.consumer.access-key}",
        secretKey = "${playground.rocketmq.consumer.secret-key}", namespace = "${playground.rocketmq.consumer.namespace}",
        filterExpressionType = "${playground.rocketmq.consumer.filter-expression-type}",
        topic = "${playground.rocketmq.topics.delay}", consumerGroup = "${playground.rocketmq.consumer-groups.delay-demo}",
        tag = "${playground.rocketmq.tags.demo}")
public class DelayDemoConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final ObjectMapper objectMapper;
    private final RocketMqLearningProperties properties;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(messageView, properties.getConsumerGroups().getDelayDemo(),
                consumerSupport.demoRouteContract(), envelope -> {
            DemoMessagePayload payload = objectMapper.treeToValue(envelope.getPayload(), DemoMessagePayload.class);
            long expectedAt = Long.parseLong(payload.getBusinessKey());
            long receivedAt = System.currentTimeMillis();
            log.info("延迟消息: expectedAt={}, brokerDeliveryAt={}, receivedAt={}, driftMillis={}, text={}",
                    expectedAt, messageView.getDeliveryTimestamp().orElse(null), receivedAt,
                    receivedAt - expectedAt, payload.getText());
        });
    }
}
