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

/** 缓存失效组；与统计、通知组消费进度互不影响。 */
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}",
        topic = RocketMqNames.NORMAL_TOPIC, consumerGroup = RocketMqNames.ORDER_CACHE_GROUP,
        tag = RocketMqNames.TAG_ORDER_CREATED + "||" + RocketMqNames.TAG_ORDER_PAID + "||"
                + RocketMqNames.TAG_ORDER_CANCELLED, sslEnabled = false)
public class OrderCacheConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final RocketOrderConsumerService consumerService;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(
                messageView, RocketMqNames.ORDER_CACHE_GROUP,
                RocketConsumerSupport.ORDER_EVENT_ROUTE_CONTRACT, consumerService::handleCache);
    }
}
