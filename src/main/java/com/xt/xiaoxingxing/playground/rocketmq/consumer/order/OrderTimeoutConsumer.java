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

/** DELAY Topic 的付款超时组；到达只触发检查，条件更新才决定是否真的取消。 */
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}",
        topic = RocketMqNames.DELAY_TOPIC, consumerGroup = RocketMqNames.ORDER_TIMEOUT_GROUP,
        tag = RocketMqNames.TAG_ORDER_TIMEOUT, sslEnabled = false)
public class OrderTimeoutConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final RocketOrderConsumerService consumerService;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(
                messageView, RocketMqNames.ORDER_TIMEOUT_GROUP,
                RocketConsumerSupport.ORDER_TIMEOUT_ROUTE_CONTRACT, consumerService::handleTimeout);
    }
}
