package com.xt.xiaoxingxing.playground.rocketmq.support;

import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * 对官方 v5 {@link RocketMQClientTemplate} 的小型发布适配器。
 *
 * <p>业务层不直接依赖 Starter 类型与目的地拼接规则。这里的同步成功只表示 Broker 已接收消息，不表示任一
 * 消费者已完成业务事务；消费成功和生产成功属于两条独立的可靠性链路。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RocketMessagePublisher {

    private final RocketMQClientTemplate rocketMQClientTemplate;
    private final RocketMessageCodec codec;

    public RocketPublishResult publishNormal(String topic,
                                             String tag,
                                             String messageKey,
                                             RocketMessageEnvelope<?> envelope) {
        if (envelope == null) {
            return nullEnvelopeFailure(topic, tag, messageKey);
        }
        try {
            SendReceipt receipt = rocketMQClientTemplate.syncSendNormalMessage(
                    destination(topic, tag), buildMessage(envelope, messageKey, null));
            return RocketPublishResult.success(envelope.getMessageId(), receipt, topic, tag, messageKey);
        } catch (Exception e) {
            return failure(envelope, topic, tag, messageKey, e);
        }
    }

    /**
     * 异步发送立即返回稳定业务 ID。
     *
     * <p>HTTP 返回 accepted 不能证明 Broker 已接收消息：最终 SendReceipt 或异常仅能在 future 回调获得。
     * 对需要可恢复投递的订单事件，仍应使用 Outbox 状态机而不是把一次异步受理当成最终成功。</p>
     */
    public RocketPublishResult publishNormalAsync(String topic,
                                                  String tag,
                                                  String messageKey,
                                                  RocketMessageEnvelope<?> envelope) {
        if (envelope == null) {
            return nullEnvelopeFailure(topic, tag, messageKey);
        }
        try {
            CompletableFuture<SendReceipt> completion = new CompletableFuture<>();
            rocketMQClientTemplate.asyncSendNormalMessage(
                    destination(topic, tag), buildMessage(envelope, messageKey, null), completion);
            completion.whenComplete((receipt, throwable) -> {
                if (throwable != null) {
                    log.error("RocketMQ异步发布失败: businessMessageId={}, topic={}, tag={}, key={}",
                            envelope.getMessageId(), topic, tag, messageKey, throwable);
                    return;
                }
                log.info("RocketMQ异步发布完成: businessMessageId={}, brokerMessageId={}, topic={}, tag={}, key={}",
                        envelope.getMessageId(), receipt.getMessageId(), topic, tag, messageKey);
            });
            return RocketPublishResult.accepted(envelope.getMessageId(), topic, tag, messageKey);
        } catch (Exception e) {
            return failure(envelope, topic, tag, messageKey, e);
        }
    }

    /** FIFO 的顺序只在同一 messageGroup 内成立，不代表整个 Topic 全局有序。 */
    public RocketPublishResult publishFifo(String topic,
                                           String tag,
                                           String messageKey,
                                           String messageGroup,
                                           RocketMessageEnvelope<?> envelope) {
        if (envelope == null) {
            return nullEnvelopeFailure(topic, tag, messageKey);
        }
        try {
            SendReceipt receipt = rocketMQClientTemplate.syncSendFifoMessage(
                    destination(topic, tag), buildMessage(envelope, messageKey, null), messageGroup);
            return RocketPublishResult.success(envelope.getMessageId(), receipt, topic, tag, messageKey);
        } catch (Exception e) {
            return failure(envelope, topic, tag, messageKey, e);
        }
    }

    /** 延迟由 DELAY Topic 和 Broker 处理；duration 只描述“何时对消费者可见”。 */
    public RocketPublishResult publishDelay(String topic,
                                            String tag,
                                            String messageKey,
                                            Duration delay,
                                            RocketMessageEnvelope<?> envelope) {
        if (envelope == null) {
            return nullEnvelopeFailure(topic, tag, messageKey);
        }
        try {
            if (delay == null || delay.isNegative() || delay.isZero()) {
                throw new IllegalArgumentException("延迟时间必须大于0");
            }
            SendReceipt receipt = rocketMQClientTemplate.syncSendDelayMessage(
                    destination(topic, tag), buildMessage(envelope, messageKey, null), delay);
            return RocketPublishResult.success(envelope.getMessageId(), receipt, topic, tag, messageKey);
        } catch (Exception e) {
            return failure(envelope, topic, tag, messageKey, e);
        }
    }

    /**
     * 发送事务半消息并返回可由业务层显式 commit/rollback 的 Transaction。
     *
     * <p>返回 Pair 不代表本地订单事务已完成：Broker 已保存但暂不投递该消息，调用方必须先完成本地数据库事务，
     * 再决定提交、回滚或等待回查。transactionId 同时写入普通消息属性，方便日志和回查链路关联。</p>
     */
    public Pair<SendReceipt, Transaction> beginTransaction(String topic,
                                                            String tag,
                                                            String messageKey,
                                                            String transactionId,
                                                            RocketMessageEnvelope<?> envelope) {
        if (envelope == null) {
            // 此方法必须返回 Client 的事务句柄，无法用 RocketPublishResult 表达失败，故采用明确的参数异常。
            throw new IllegalArgumentException("RocketMQ消息信封不能为空");
        }
        return rocketMQClientTemplate.sendTransactionMessage(
                destination(topic, tag), buildMessage(envelope, messageKey, transactionId));
    }

    private Message<String> buildMessage(RocketMessageEnvelope<?> envelope,
                                         String messageKey,
                                         String transactionId) {
        if (messageKey == null || messageKey.isBlank()) {
            throw new IllegalArgumentException("RocketMQ消息Key不能为空");
        }
        MessageBuilder<String> builder = MessageBuilder.withPayload(codec.toJson(envelope))
                // KEYS 是 RocketMQ 可查询业务键，不是 Spring 的普通日志 Header。
                .setHeader(RocketMQHeaders.KEYS, messageKey);
        if (transactionId != null && !transactionId.isBlank()) {
            builder.setHeader(RocketMqNames.HEADER_TRANSACTION_ID, transactionId);
        }
        return builder.build();
    }

    private String destination(String topic, String tag) {
        if (topic == null || topic.isBlank() || tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("RocketMQ Topic和Tag不能为空");
        }
        return topic + ":" + tag;
    }

    private RocketPublishResult failure(RocketMessageEnvelope<?> envelope,
                                        String topic,
                                        String tag,
                                        String messageKey,
                                        Exception exception) {
        String reason = exception.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = exception.getClass().getSimpleName();
        }
        return RocketPublishResult.failure(businessMessageId(envelope), topic, tag, messageKey, "发布异常: " + reason);
    }

    /** 四种非事务发布对 null 信封保持完全一致的结果语义，不让错误处理再抛 NPE 掩盖根因。 */
    private RocketPublishResult nullEnvelopeFailure(String topic, String tag, String messageKey) {
        return RocketPublishResult.failure(null, topic, tag, messageKey, "RocketMQ消息信封不能为空");
    }

    /** 防御未来新增发布路径在失败阶段传入 null；异常转换本身不应再产生第二个异常。 */
    private String businessMessageId(RocketMessageEnvelope<?> envelope) {
        return envelope == null ? null : envelope.getMessageId();
    }
}
