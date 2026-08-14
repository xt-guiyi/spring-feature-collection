package com.xt.xiaoxingxing.playground.rocketmq.consumer.order;

import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningProperties;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqLearningListener;
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
@RocketMqLearningListener
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}", accessKey = "${playground.rocketmq.consumer.access-key}",
        secretKey = "${playground.rocketmq.consumer.secret-key}", namespace = "${playground.rocketmq.consumer.namespace}",
        filterExpressionType = "${playground.rocketmq.consumer.filter-expression-type}",
        topic = "${playground.rocketmq.topics.normal}", consumerGroup = "${playground.rocketmq.consumer-groups.order-cache}",
        tag = "${playground.rocketmq.tags.order-created}||${playground.rocketmq.tags.order-paid}||${playground.rocketmq.tags.order-cancelled}")
public class OrderCacheConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final RocketOrderConsumerService consumerService;
    private final RocketMqLearningProperties properties;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(
                messageView, properties.getConsumerGroups().getOrderCache(),
                consumerSupport.orderEventRouteContract(), consumerService::handleCache);
    }
}
