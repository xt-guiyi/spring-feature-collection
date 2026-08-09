package com.xt.xiaoxingxing.playground.rabbitmq.consumer;

import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.MessageHandler;
import com.xt.xiaoxingxing.playground.rabbitmq.config.RabbitMqNames;
import com.xt.xiaoxingxing.playground.rabbitmq.message.RabbitMessageEnvelope;
import com.xt.xiaoxingxing.playground.rabbitmq.support.RabbitMessageCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Stream 审计消费者：消息处理成功后手动保存 offset，消息本身仍保留在 Stream 中。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAuditStreamConsumer {

    private final RabbitMessageCodec messageCodec;

    @RabbitListener(
            id = "pg-order-audit-stream-listener",
            queues = RabbitMqNames.ORDER_AUDIT_STREAM,
            containerFactory = "rabbitStreamListenerContainerFactory"
    )
    public void consume(Message message, MessageHandler.Context context) {
        /*
         * 完整步骤：
         * 第1步：获取 Stream 原生消息体；
         * 第2步：复用 AMQP 完全相同的版本化 JSON 信封解析；
         * 第3步：记录 offset、事件类型和聚合 ID；
         * 第4步：业务处理成功后手动保存 offset。
         *
         * 如果第3步抛异常，代码不会执行 storeOffset，应用恢复后仍可以从旧 offset 继续读取。
         */
        RabbitMessageEnvelope<com.fasterxml.jackson.databind.JsonNode> envelope =
                messageCodec.decode(message.getBodyAsBinary());
        log.info("Stream审计事件: offset={}, messageId={}, eventType={}, aggregateId={}",
                context.offset(), envelope.getMessageId(), envelope.getEventType(), envelope.getAggregateId());
        context.storeOffset();
    }
}
