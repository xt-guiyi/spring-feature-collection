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

/** 事务消息组：只有 COMMIT 的半消息才可见，ROLLED_BACK 半消息不会进入本监听器。 */
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(endpoints = "${playground.rocketmq.endpoints}",
        topic = RocketMqNames.TRANSACTION_TOPIC, consumerGroup = RocketMqNames.TRANSACTION_ORDER_GROUP,
        tag = RocketMqNames.TAG_ORDER_CREATED, sslEnabled = false)
public class TransactionOrderConsumer implements RocketMQListener {

    private final RocketConsumerSupport consumerSupport;
    private final RocketOrderConsumerService consumerService;

    @Override
    public ConsumeResult consume(MessageView messageView) {
        return consumerSupport.handle(
                messageView, RocketMqNames.TRANSACTION_ORDER_GROUP,
                RocketConsumerSupport.TRANSACTION_ORDER_ROUTE_CONTRACT, consumerService::handleTransactionOrder);
    }
}
