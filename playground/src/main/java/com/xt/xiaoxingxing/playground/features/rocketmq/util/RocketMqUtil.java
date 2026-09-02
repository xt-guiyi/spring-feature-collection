package com.xt.xiaoxingxing.playground.features.rocketmq.util;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.apache.rocketmq.client.common.Pair;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.apache.rocketmq.client.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** RocketMQ 消息发送工具。 */
@Component
@RequiredArgsConstructor
public class RocketMqUtil {

    private final RocketMQClientTemplate rocketMQClientTemplate;

    /** 发送普通消息。 */
    public SendReceipt send(String topic, String tag, String key, Object payload) {
        return rocketMQClientTemplate.syncSendNormalMessage(
                topic + ":" + tag,
                buildMessage(key, payload));
    }

    /** 异步发送普通消息。 */
    public CompletableFuture<SendReceipt> sendAsync(String topic,
                                                    String tag,
                                                    String key,
                                                    Object payload) {
        return rocketMQClientTemplate.asyncSendNormalMessage(
                topic + ":" + tag,
                buildMessage(key, payload),
                null);
    }

    /** 发送顺序消息。 */
    public SendReceipt sendFifo(String topic,
                                String tag,
                                String key,
                                Object payload,
                                String messageGroup) {
        return rocketMQClientTemplate.syncSendFifoMessage(
                topic + ":" + tag,
                buildMessage(key, payload),
                messageGroup);
    }

    /** 异步发送顺序消息。 */
    public CompletableFuture<SendReceipt> sendFifoAsync(String topic,
                                                        String tag,
                                                        String key,
                                                        Object payload,
                                                        String messageGroup) {
        return rocketMQClientTemplate.asyncSendFifoMessage(
                topic + ":" + tag,
                buildMessage(key, payload),
                messageGroup,
                null);
    }

    /** 发送延迟消息。 */
    public SendReceipt sendDelay(String topic,
                                 String tag,
                                 String key,
                                 Object payload,
                                 Duration delay) {
        return rocketMQClientTemplate.syncSendDelayMessage(
                topic + ":" + tag,
                buildMessage(key, payload),
                delay);
    }

    /** 异步发送延迟消息。 */
    public CompletableFuture<SendReceipt> sendDelayAsync(String topic,
                                                         String tag,
                                                         String key,
                                                         Object payload,
                                                         Duration delay) {
        return rocketMQClientTemplate.asyncSendDelayMessage(
                topic + ":" + tag,
                buildMessage(key, payload),
                delay,
                null);
    }

    /** 发送事务消息。 */
    public Pair<SendReceipt, Transaction> sendTransaction(String topic,
                                                          String tag,
                                                          String key,
                                                          Object payload) {
        return rocketMQClientTemplate.sendTransactionMessage(
                topic + ":" + tag,
                buildMessage(key, payload));
    }

    /** 构建 RocketMQ 消息。 */
    private Message<Object> buildMessage(String key, Object payload) {
        return MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, key)
                .build();
    }
}
