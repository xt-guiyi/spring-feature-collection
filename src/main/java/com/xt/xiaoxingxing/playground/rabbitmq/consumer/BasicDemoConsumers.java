package com.xt.xiaoxingxing.playground.rabbitmq.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.xt.xiaoxingxing.playground.rabbitmq.config.RabbitMqNames;
import com.xt.xiaoxingxing.playground.rabbitmq.enums.RabbitAckAction;
import com.xt.xiaoxingxing.playground.rabbitmq.message.DemoMessagePayload;
import com.xt.xiaoxingxing.playground.rabbitmq.message.RabbitMessageEnvelope;
import com.xt.xiaoxingxing.playground.rabbitmq.support.MessageDecodingException;
import com.xt.xiaoxingxing.playground.rabbitmq.support.RabbitConsumerSupport;
import com.xt.xiaoxingxing.playground.rabbitmq.support.RabbitMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Classic Queue 的基础路由、确认和顺序消费案例。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BasicDemoConsumers {

    private final RabbitMessageCodec messageCodec;
    private final RabbitConsumerSupport consumerSupport;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqNames.DIRECT_EMAIL_QUEUE, containerFactory = "rabbitManualContainerFactory")
    public void consumeDirect(Message message, Channel channel) throws IOException {
        logAndAck(message, channel, RabbitMqNames.DIRECT_EMAIL_QUEUE);
    }

    @RabbitListener(queues = RabbitMqNames.TOPIC_ORDER_QUEUE, containerFactory = "rabbitManualContainerFactory")
    public void consumeTopicOrder(Message message, Channel channel) throws IOException {
        logAndAck(message, channel, RabbitMqNames.TOPIC_ORDER_QUEUE);
    }

    @RabbitListener(queues = RabbitMqNames.TOPIC_PAID_QUEUE, containerFactory = "rabbitManualContainerFactory")
    public void consumeTopicPaid(Message message, Channel channel) throws IOException {
        logAndAck(message, channel, RabbitMqNames.TOPIC_PAID_QUEUE);
    }

    @RabbitListener(queues = RabbitMqNames.FANOUT_QUEUE_A, containerFactory = "rabbitManualContainerFactory")
    public void consumeFanoutA(Message message, Channel channel) throws IOException {
        logAndAck(message, channel, RabbitMqNames.FANOUT_QUEUE_A);
    }

    @RabbitListener(queues = RabbitMqNames.FANOUT_QUEUE_B, containerFactory = "rabbitManualContainerFactory")
    public void consumeFanoutB(Message message, Channel channel) throws IOException {
        logAndAck(message, channel, RabbitMqNames.FANOUT_QUEUE_B);
    }

    /**
     * 同一个队列中演示四种手动确认行为。
     *
     * <p>NACK_REQUEUE_ONCE 必须检查 redelivered：如果每次收到都 requeue=true，消息会立即回到队列并不断重试，
     * 消费线程和日志都会被打满。</p>
     */
    @RabbitListener(queues = RabbitMqNames.ACK_DEMO_QUEUE, containerFactory = "rabbitManualContainerFactory")
    public void consumeAckDemo(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        RabbitMessageEnvelope<com.fasterxml.jackson.databind.JsonNode> envelope;
        DemoMessagePayload payload;
        RabbitAckAction action;
        try {
            envelope = messageCodec.decode(message);
            payload = toDemoPayload(envelope);
            action = RabbitAckAction.valueOf(payload.getAction());
        } catch (RuntimeException exception) {
            channel.basicReject(deliveryTag, false);
            log.error("ACK案例消息格式或action不合法，已Reject", exception);
            return;
        }

        switch (action) {
            case ACK -> {
                channel.basicAck(deliveryTag, false);
                log.info("ACK案例成功确认: messageId={}", envelope.getMessageId());
            }
            case NACK_REQUEUE_ONCE -> {
                if (!Boolean.TRUE.equals(message.getMessageProperties().getRedelivered())) {
                    channel.basicNack(deliveryTag, false, true);
                    log.warn("NACK案例第一次处理，requeue=true: messageId={}", envelope.getMessageId());
                } else {
                    channel.basicAck(deliveryTag, false);
                    log.info("NACK案例重新投递后成功ACK: messageId={}", envelope.getMessageId());
                }
            }
            case REJECT_TO_DEAD -> {
                channel.basicReject(deliveryTag, false);
                log.warn("Reject案例进入死信交换机: messageId={}", envelope.getMessageId());
            }
            case RETRY_THEN_SUCCESS -> consumerSupport.handle(
                    message,
                    channel,
                    "classic-retry-demo-consumer",
                    RabbitMqNames.ACK_DEMO_RETRY_KEY,
                    ignored -> {
                        int currentRetry = readRetryCount(message);
                        int failTimes = payload.getFailTimes() == null ? 0 : payload.getFailTimes();
                        if (currentRetry < failTimes) {
                            throw new IllegalStateException("学习案例主动制造第" + (currentRetry + 1) + "次失败");
                        }
                        log.info("TTL重试后处理成功: messageId={}, retryCount={}",
                                envelope.getMessageId(), currentRetry);
                    });
        }
    }

    @RabbitListener(queues = RabbitMqNames.ORDERING_DEMO_QUEUE, containerFactory = "rabbitOrderedContainerFactory")
    public void consumeOrdering(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            RabbitMessageEnvelope<com.fasterxml.jackson.databind.JsonNode> envelope = messageCodec.decode(message);
            DemoMessagePayload payload = toDemoPayload(envelope);
            log.info("顺序消费者收到消息: businessKey={}, sequence={}, messageId={}",
                    payload.getBusinessKey(), payload.getSequence(), envelope.getMessageId());
        } catch (RuntimeException e) {
            channel.basicReject(deliveryTag, false);
            log.error("顺序消息格式错误，已拒绝", e);
            return;
        }
        // ACK异常直接向外传播，不当作消息格式错误处理。
        channel.basicAck(deliveryTag, false);
    }

    private void logAndAck(Message message, Channel channel, String queueName) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            RabbitMessageEnvelope<com.fasterxml.jackson.databind.JsonNode> envelope = messageCodec.decode(message);
            DemoMessagePayload payload = toDemoPayload(envelope);
            log.info("基础消费者收到消息: queue={}, exchange={}, routingKey={}, text={}, messageId={}",
                    queueName,
                    message.getMessageProperties().getReceivedExchange(),
                    message.getMessageProperties().getReceivedRoutingKey(),
                    payload.getText(),
                    envelope.getMessageId());
        } catch (Exception e) {
            channel.basicReject(deliveryTag, false);
            log.error("基础消息无法解析，已Reject: queue={}", queueName, e);
            return;
        }

        // 解析和日志业务正常结束后才确认；multiple=false 表示只确认当前 deliveryTag。
        channel.basicAck(deliveryTag, false);
    }

    private DemoMessagePayload toDemoPayload(RabbitMessageEnvelope<?> envelope) {
        try {
            return objectMapper.treeToValue(
                    (com.fasterxml.jackson.databind.JsonNode) envelope.getPayload(), DemoMessagePayload.class);
        } catch (JsonProcessingException | ClassCastException e) {
            throw new MessageDecodingException("Demo消息payload格式不正确", e);
        }
    }

    private int readRetryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(RabbitMqNames.HEADER_RETRY_COUNT);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
