package com.xt.xiaoxingxing.playground.rabbitmq.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.rabbitmq.client.Channel;
import com.xt.xiaoxingxing.playground.rabbitmq.config.RabbitMqLearningProperties;
import com.xt.xiaoxingxing.playground.rabbitmq.config.RabbitMqNames;
import com.xt.xiaoxingxing.playground.rabbitmq.message.RabbitMessageEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 消费者手动确认、重试和死信的统一模板。
 *
 * <p>所有业务监听器复用这一个模板，避免某个消费者忘记 ACK 或把 requeue 写反。模板只决定消息生命周期，
 * 具体数据库事务仍放在独立 Service 中，确保业务方法正常返回时事务已经提交。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitConsumerSupport {

    private final RabbitMessageCodec messageCodec;
    private final ReliableRabbitPublisher publisher;
    private final RabbitMqLearningProperties properties;

    public void handle(Message message,
                       Channel channel,
                       String consumerName,
                       String retryRoutingKey,
                       MessageBusinessHandler handler) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        RabbitMessageEnvelope<JsonNode> envelope;

        try {
            envelope = messageCodec.decode(message);
            handler.handle(envelope);
        } catch (UnsupportedMessageVersionException | MessageDecodingException e) {
            // JSON损坏或版本不支持，重复执行不会恢复，直接拒绝并让业务队列 DLX 保存到最终死信队列。
            channel.basicReject(deliveryTag, false);
            log.error("消费者Reject不可恢复消息: consumer={}, reason={}", consumerName, e.getMessage());
            return;
        } catch (Exception businessException) {
            retryOrDeadLetter(message, channel, consumerName, retryRoutingKey, businessException);
            return;
        }

        /*
         * handler 调用的是另一个 Spring Service Bean。它正常返回时，事务拦截器已经完成数据库提交。
         * basicAck 放在业务 try/catch 外：若 ACK 自己因为 Channel 断开抛 IOException，应直接向外传播，
         * 由 Broker 在连接恢复后重新投递未确认原消息，而不是被误判为业务异常再制造一个重试副本。
         */
        channel.basicAck(deliveryTag, false);
        log.info("消费者ACK: consumer={}, messageId={}, retryCount={}",
                consumerName, envelope.getMessageId(), readRetryCount(message));
    }

    private void retryOrDeadLetter(Message original,
                                   Channel channel,
                                   String consumerName,
                                   String retryRoutingKey,
                                   Exception businessException) throws IOException {
        int currentRetry = readRetryCount(original);
        long deliveryTag = original.getMessageProperties().getDeliveryTag();
        String messageId = original.getMessageProperties().getMessageId();

        if (currentRetry >= properties.getMaxConsumeRetries()) {
            // 重试次数耗尽：reject(false) 不回原队列，转交主队列配置的死信交换机。
            channel.basicReject(deliveryTag, false);
            log.error("消费者重试耗尽，消息进入DLQ: consumer={}, messageId={}, retryCount={}",
                    consumerName, messageId, currentRetry, businessException);
            return;
        }

        Message retryMessage = MessageBuilder.fromClonedMessage(original)
                .setHeader(RabbitMqNames.HEADER_RETRY_COUNT, currentRetry + 1)
                .build();

        RabbitPublishResult retryResult = publisher.publishMessageAndWait(
                chooseRetryExchange(retryRoutingKey),
                retryRoutingKey,
                retryMessage,
                messageId == null ? "unknown-message" : messageId);

        if (retryResult.isSuccess()) {
            /*
             * 新副本已经安全进入重试队列，才 ACK 原消息。
             * 如果先 ACK 再发布，而发布失败，原消息与重试副本都会不存在，造成真正丢失。
             */
            channel.basicAck(deliveryTag, false);
            log.warn("消费失败，已进入延迟重试队列: consumer={}, messageId={}, nextRetry={}, reason={}",
                    consumerName, messageId, currentRetry + 1, conciseMessage(businessException));
        } else {
            /*
             * 重试副本没有得到 Broker 的安全确认，此时不能 ACK 原消息。
             * nack(requeue=true) 让原消息回原队列；这可能短时间重复，但不会静默丢失。
             */
            channel.basicNack(deliveryTag, false, true);
            log.error("重试消息发布失败，原消息重新入队: consumer={}, messageId={}, reason={}",
                    consumerName, messageId, retryResult.getReason());
        }
    }

    private String chooseRetryExchange(String retryRoutingKey) {
        return RabbitMqNames.ACK_DEMO_RETRY_KEY.equals(retryRoutingKey)
                ? RabbitMqNames.LEARNING_RETRY_EXCHANGE
                : RabbitMqNames.ORDER_RETRY_EXCHANGE;
    }

    private int readRetryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(RabbitMqNames.HEADER_RETRY_COUNT);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    @FunctionalInterface
    public interface MessageBusinessHandler {
        void handle(RabbitMessageEnvelope<JsonNode> envelope) throws Exception;
    }
}
