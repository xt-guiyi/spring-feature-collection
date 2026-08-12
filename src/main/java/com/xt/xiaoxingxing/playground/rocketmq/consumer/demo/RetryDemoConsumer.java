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

/**
 * 主动失败前 N 次以观察 Broker 重投；不在 Java 中自行复制重试消息。
 * 最大重试次数属于 Broker ConsumerGroup 配置，不由 Java Listener 注解决定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}",
        topic = RocketMqNames.NORMAL_TOPIC, consumerGroup = RocketMqNames.RETRY_DEMO_GROUP,
        tag = RocketMqNames.TAG_RETRY, sslEnabled = false)
public class RetryDemoConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final ObjectMapper objectMapper;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(messageView, RocketMqNames.RETRY_DEMO_GROUP,
                RocketConsumerSupport.RETRY_ROUTE_CONTRACT, envelope -> {
            DemoMessagePayload payload = objectMapper.treeToValue(envelope.getPayload(), DemoMessagePayload.class);
            int failTimes = payload.getFailTimes() == null ? 0 : payload.getFailTimes();
            int attempt = messageView.getDeliveryAttempt();
            if (attempt <= failTimes) {
                // 抛异常后公共模板返回 FAILURE；Broker 负责重投，耗尽策略后进入 %DLQ%<consumerGroup>。
                throw new IllegalStateException("学习案例主动制造第" + attempt + "次消费失败");
            }
            log.info("重试案例最终成功: businessMessageId={}, deliveryAttempt={}, failTimes={}",
                    envelope.getMessageId(), attempt, failTimes);
        });
    }
}
