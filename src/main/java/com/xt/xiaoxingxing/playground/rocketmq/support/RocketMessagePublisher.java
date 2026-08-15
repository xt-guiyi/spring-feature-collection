package com.xt.xiaoxingxing.playground.rocketmq.support;

import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.apache.rocketmq.client.common.Pair;
import org.apache.rocketmq.client.core.RocketMQClientTemplate;
import org.apache.rocketmq.client.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 对官方 v5 {@link RocketMQClientTemplate} 的小型发布适配器。
 *
 * <p>业务层不直接依赖 Starter 类型与目的地拼接规则。这里的同步成功只表示 Broker 已接收消息，不表示任一
 * 消费者已完成业务事务；消费成功和生产成功属于两条独立的可靠性链路。</p>
 */
@Component
@RequiredArgsConstructor
public class RocketMessagePublisher {

    private final RocketMQClientTemplate rocketMQClientTemplate;
    private final RocketMessageCodec codec;

    public String publishNormal(String topic,
                                String tag,
                                String messageKey,
                                RocketMessageEnvelope<?> envelope) {
        requireEnvelope(envelope);
        SendReceipt receipt = rocketMQClientTemplate.syncSendNormalMessage(
                destination(topic, tag), buildMessage(envelope, messageKey));
        return brokerMessageId(receipt);
    }

    /** 延迟由 DELAY Topic 和 Broker 处理；delaySeconds 只描述“多久后对消费者可见”。 */
    public String publishDelay(String topic,
                               String tag,
                               String messageKey,
                               long delaySeconds,
                               RocketMessageEnvelope<?> envelope) {
        requireEnvelope(envelope);
        if (delaySeconds <= 0) {
            throw new IllegalArgumentException("延迟时间必须大于0");
        }
        SendReceipt receipt = rocketMQClientTemplate.syncSendDelayMessage(
                destination(topic, tag), buildMessage(envelope, messageKey),
                java.time.Duration.ofSeconds(delaySeconds));
        return brokerMessageId(receipt);
    }

    /**
     * 发送事务半消息并返回可由业务层显式 commit/rollback 的 Transaction。
     *
     * <p>返回 TransactionHandle 不代表本地订单事务已完成：Broker 已保存但暂不投递该消息，
     * 调用方必须先完成本地数据库事务，
     * 再决定提交、回滚或等待回查。事务记录主键与信封 messageId 使用同一个值，回查不再依赖
     * 自定义 Header 中的第二份事务标识。</p>
     */
    public TransactionHandle beginTransaction(String topic,
                                              String tag,
                                              String messageKey,
                                              RocketMessageEnvelope<?> envelope) {
        requireEnvelope(envelope);
        Pair<SendReceipt, Transaction> result = rocketMQClientTemplate.sendTransactionMessage(
                destination(topic, tag), buildMessage(envelope, messageKey));
        return new TransactionHandle(brokerMessageId(result.getSendReceipt()), result.getTransaction());
    }

    private Message<String> buildMessage(RocketMessageEnvelope<?> envelope, String messageKey) {
        if (messageKey == null || messageKey.isBlank()) {
            throw new IllegalArgumentException("RocketMQ消息Key不能为空");
        }
        return MessageBuilder.withPayload(codec.toJson(envelope))
                // KEYS 是 RocketMQ 可查询业务键，不是 Spring 的普通日志 Header。
                .setHeader(RocketMQHeaders.KEYS, messageKey)
                .build();
    }

    private String destination(String topic, String tag) {
        if (topic == null || topic.isBlank() || tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("RocketMQ Topic和Tag不能为空");
        }
        return topic + ":" + tag;
    }

    private void requireEnvelope(RocketMessageEnvelope<?> envelope) {
        if (envelope == null) {
            throw new IllegalArgumentException("RocketMQ消息信封不能为空");
        }
    }

    private String brokerMessageId(SendReceipt receipt) {
        if (receipt == null || receipt.getMessageId() == null) {
            throw new IllegalStateException("RocketMQ Broker已返回发送结果，但缺少brokerMessageId");
        }
        return receipt.getMessageId().toString();
    }

    /**
     * 封装 RocketMQ Client 的事务对象，业务层只需理解“半消息提交/回滚”。
     * Client 具体的 Pair、SendReceipt 结构不再向 Service 传播。
     */
    public static final class TransactionHandle {

        @Getter
        private final String brokerMessageId;
        private final Transaction transaction;

        private TransactionHandle(String brokerMessageId, Transaction transaction) {
            this.brokerMessageId = brokerMessageId;
            this.transaction = transaction;
        }

        public void commit() throws ClientException {
            transaction.commit();
        }

        public void rollback() throws ClientException {
            transaction.rollback();
        }
    }
}
