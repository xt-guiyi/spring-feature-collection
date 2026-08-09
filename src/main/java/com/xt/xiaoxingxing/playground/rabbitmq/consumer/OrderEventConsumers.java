package com.xt.xiaoxingxing.playground.rabbitmq.consumer;

import com.rabbitmq.client.Channel;
import com.xt.xiaoxingxing.playground.rabbitmq.config.RabbitMqNames;
import com.xt.xiaoxingxing.playground.rabbitmq.service.RabbitOrderConsumerService;
import com.xt.xiaoxingxing.playground.rabbitmq.support.RabbitConsumerSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 四个相互独立的订单消费者。
 *
 * <p>同一 ORDER_CREATED 会复制到缓存、统计、通知三个队列。某个消费者失败只会重试自己的队列，
 * 已经成功的消费者不会被迫重新执行，这正是“一个副作用一个队列”的价值。</p>
 */
@Component
@RequiredArgsConstructor
public class OrderEventConsumers {

    private final RabbitConsumerSupport consumerSupport;
    private final RabbitOrderConsumerService consumerService;

    @RabbitListener(queues = RabbitMqNames.ORDER_CACHE_QUEUE, containerFactory = "rabbitManualContainerFactory")
    public void consumeCache(Message message, Channel channel) throws IOException {
        consumerSupport.handle(message, channel, RabbitMqNames.CACHE_CONSUMER,
                RabbitMqNames.ORDER_CACHE_RETRY_KEY, consumerService::handleCache);
    }

    @RabbitListener(queues = RabbitMqNames.ORDER_STATISTICS_QUEUE, containerFactory = "rabbitManualContainerFactory")
    public void consumeStatistics(Message message, Channel channel) throws IOException {
        consumerSupport.handle(message, channel, RabbitMqNames.STATISTICS_CONSUMER,
                RabbitMqNames.ORDER_STATISTICS_RETRY_KEY, consumerService::handleStatistics);
    }

    @RabbitListener(queues = RabbitMqNames.ORDER_NOTIFICATION_QUEUE, containerFactory = "rabbitManualContainerFactory")
    public void consumeNotification(Message message, Channel channel) throws IOException {
        consumerSupport.handle(message, channel, RabbitMqNames.NOTIFICATION_CONSUMER,
                RabbitMqNames.ORDER_NOTIFICATION_RETRY_KEY, consumerService::handleNotification);
    }

    @RabbitListener(queues = RabbitMqNames.ORDER_TIMEOUT_QUEUE, containerFactory = "rabbitManualContainerFactory")
    public void consumeTimeout(Message message, Channel channel) throws IOException {
        consumerSupport.handle(message, channel, RabbitMqNames.TIMEOUT_CONSUMER,
                RabbitMqNames.ORDER_TIMEOUT_RETRY_KEY, consumerService::handleTimeout);
    }
}
