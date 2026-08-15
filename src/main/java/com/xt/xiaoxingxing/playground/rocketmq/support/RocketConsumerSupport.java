package com.xt.xiaoxingxing.playground.rocketmq.support;

import tools.jackson.databind.JsonNode;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.springframework.stereotype.Component;

/**
 * 所有监听器共用的消费生命周期模板。
 *
 * <p>该模板不在 Java 里制造重试副本。业务抛异常时返回 FAILURE，由 RocketMQ Broker 管理重新投递次数和
 * DLQ 路由；因此各消费者只需专注于“协议与路由契约通过且业务事务完成前绝不返回成功”
 * 和数据库幂等。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RocketConsumerSupport {

    private final RocketMessageCodec codec;
    /**
     * 第1步解码并校验信封协议；
     * 第2步取得 Broker 实际投递的 Tag；
     * 第3步调用具体业务 handler；
     * 第4步仅在业务正常返回后确认 SUCCESS。
     *
     * <p>任何异常均返回 FAILURE，Broker 后续重试；包括损坏 JSON、不支持版本和路由契约不匹配。
     * 它们通常无法靠重试自愈，但有限重试后进入 DLQ 可保留故障证据而非静默丢弃。</p>
     */
    public ConsumeResult handle(MessageView messageView,
                                String consumerName,
                                MessageHandler handler) {
        try {
            RocketMessageEnvelope<JsonNode> envelope = codec.decode(messageView);
            String actualTag = messageView.getTag()
                    .orElseThrow(() -> new MessageDecodingException("RocketMQ消息缺少Tag"));
            handler.handle(actualTag, envelope);
            log.info("RocketMQ消费成功: consumer={}, tag={}, brokerMessageId={}, businessMessageId={}, deliveryAttempt={}",
                    consumerName, actualTag, messageView.getMessageId(), envelope.getMessageId(),
                    messageView.getDeliveryAttempt());
            return ConsumeResult.SUCCESS;
        } catch (Exception exception) {
            log.error("RocketMQ消费失败，交由Broker重试或DLQ: consumer={}, brokerMessageId={}, deliveryAttempt={}",
                    consumerName,
                    messageView == null ? null : messageView.getMessageId(),
                    messageView == null ? null : messageView.getDeliveryAttempt(),
                    exception);
            return ConsumeResult.FAILURE;
        }
    }

    @FunctionalInterface
    public interface MessageHandler {
        void handle(String actualTag, RocketMessageEnvelope<JsonNode> envelope) throws Exception;
    }
}
