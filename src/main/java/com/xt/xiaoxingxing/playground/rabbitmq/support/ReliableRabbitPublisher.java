package com.xt.xiaoxingxing.playground.rabbitmq.support;

import com.xt.xiaoxingxing.playground.rabbitmq.config.RabbitMqLearningProperties;
import com.xt.xiaoxingxing.playground.rabbitmq.message.RabbitMessageEnvelope;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

/**
 * 等待 Publisher Confirm 并检查 Mandatory Return 的可靠发布器。
 *
 * <p>Confirm 只回答“Broker 是否接管了消息”，不代表消费者已经执行成功。消费者 ACK 是另一条完全独立的确认链路。
 * 此外，Broker 可能对一条无法路由的消息返回 ACK，因此还必须同时检查 Return。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReliableRabbitPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMessageCodec codec;
    private final RabbitMqLearningProperties properties;

    @PostConstruct
    public void registerReturnsLogger() {
        // 回调用于学习日志和全局观察；每条消息是否成功仍以 CorrelationData.getReturned() 为准。
        rabbitTemplate.setReturnsCallback(returned -> log.warn(
                "RabbitMQ消息被退回: replyCode={}, replyText={}, exchange={}, routingKey={}, messageId={}",
                returned.getReplyCode(), returned.getReplyText(), returned.getExchange(), returned.getRoutingKey(),
                returned.getMessage().getMessageProperties().getMessageId()));
    }

    public RabbitPublishResult publishAndWait(String exchange,
                                              String routingKey,
                                              RabbitMessageEnvelope<?> envelope) {
        /*
         * 完整步骤：
         * 第1步：把统一信封编码为持久化 JSON 消息；
         * 第2步：messageId 同时作为 CorrelationData ID 发送；
         * 第3步：限时等待 Broker Confirm；
         * 第4步：即使收到 ACK，也检查 mandatory Return；
         * 第5步：只有“ACK 且未退回”才返回成功。
         */
        Message message = codec.encode(envelope);
        return publishMessageAndWait(exchange, routingKey, message, envelope.getMessageId());
    }

    /**
     * 发布已经构造好的 AMQP Message，主要给“带 x-retry-count Header 的重试副本”使用。
     * messageId 保持不变以继续幂等，CorrelationData ID 增加一次发布后缀以区分并发发送尝试。
     */
    public RabbitPublishResult publishMessageAndWait(String exchange,
                                                     String routingKey,
                                                     Message message,
                                                     String messageId) {
        String correlationId = messageId + ":" + UUID.randomUUID();
        CorrelationData correlationData = new CorrelationData(correlationId);

        try {
            rabbitTemplate.send(exchange, routingKey, message, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(properties.getConfirmTimeoutSeconds(), TimeUnit.SECONDS);

            if (!confirm.isAck()) {
                String reason = confirm.getReason() == null ? "Broker返回Publisher NACK" : confirm.getReason();
                return RabbitPublishResult.failure(messageId, exchange, routingKey, reason);
            }

            // Spring AMQP 保证 Return 会在 Confirm Future 完成前设置到 CorrelationData。
            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                String reason = "消息无法路由: " + returned.getReplyCode() + " " + returned.getReplyText();
                return RabbitPublishResult.failure(messageId, exchange, routingKey, reason);
            }

            return RabbitPublishResult.success(messageId, exchange, routingKey);
        } catch (TimeoutException e) {
            return RabbitPublishResult.failure(messageId, exchange, routingKey,
                    "等待Publisher Confirm超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RabbitPublishResult.failure(messageId, exchange, routingKey,
                    "等待Publisher Confirm时线程被中断");
        } catch (Exception e) {
            return RabbitPublishResult.failure(messageId, exchange, routingKey,
                    "发布异常: " + conciseMessage(e));
        }
    }

    private String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
