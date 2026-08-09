package com.xt.xiaoxingxing.playground.rabbitmq.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xt.xiaoxingxing.playground.rabbitmq.config.RabbitMqNames;
import com.xt.xiaoxingxing.playground.rabbitmq.message.RabbitMessageEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 统一负责“Java 消息信封 ↔ AMQP JSON 消息”。
 *
 * <p>这里显式使用项目现有 ObjectMapper 转成字节，不依赖默认 Java 序列化。Java 原生序列化会把类名和实现细节
 * 带进消息，跨语言困难并且存在安全风险；JSON 更便于在 RabbitMQ 管理界面和日志中观察。</p>
 */
@Component
@RequiredArgsConstructor
public class RabbitMessageCodec {

    private final ObjectMapper objectMapper;

    /** 创建当前版本的新消息信封。 */
    public RabbitMessageEnvelope<JsonNode> newEnvelope(String eventType, String aggregateId, Object payload) {
        RabbitMessageEnvelope<JsonNode> envelope = new RabbitMessageEnvelope<>();
        envelope.setMessageId(UUID.randomUUID().toString());
        envelope.setEventType(eventType);
        envelope.setSchemaVersion(RabbitMqNames.CURRENT_SCHEMA_VERSION);
        envelope.setAggregateId(aggregateId);
        envelope.setOccurredAt(LocalDateTime.now());
        envelope.setPayload(objectMapper.valueToTree(payload));
        return envelope;
    }

    /**
     * 编码为 RabbitMQ Message，并显式声明持久化和 JSON 元数据。
     *
     * <p>deliveryMode=PERSISTENT 只能表示消息应落盘；要让重启后仍可恢复，还必须搭配 durable 队列和交换机。</p>
     */
    public Message encode(RabbitMessageEnvelope<?> envelope) {
        validateEnvelope(envelope);
        try {
            byte[] body = objectMapper.writeValueAsBytes(envelope);
            return MessageBuilder.withBody(body)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(envelope.getMessageId())
                    .setHeader(RabbitMqNames.HEADER_SCHEMA_VERSION, envelope.getSchemaVersion())
                    .build();
        } catch (JsonProcessingException e) {
            throw new MessageDecodingException("RabbitMQ消息序列化失败", e);
        }
    }

    /** 解码 AMQP 消息，并在进入具体业务前完成协议级校验。 */
    public RabbitMessageEnvelope<JsonNode> decode(Message message) {
        try {
            RabbitMessageEnvelope<JsonNode> envelope = objectMapper.readValue(
                    message.getBody(),
                    objectMapper.getTypeFactory().constructParametricType(RabbitMessageEnvelope.class, JsonNode.class));
            validateEnvelope(envelope);
            if (envelope.getSchemaVersion() != RabbitMqNames.CURRENT_SCHEMA_VERSION) {
                throw new UnsupportedMessageVersionException(
                        "不支持的消息版本: " + envelope.getSchemaVersion()
                                + "，当前仅支持: " + RabbitMqNames.CURRENT_SCHEMA_VERSION);
            }
            return envelope;
        } catch (UnsupportedMessageVersionException e) {
            throw e;
        } catch (Exception e) {
            throw new MessageDecodingException("RabbitMQ消息JSON无法解析", e);
        }
    }

    /** Stream 原生消费者收到的是字节数组，仍复用完全相同的信封协议。 */
    public RabbitMessageEnvelope<JsonNode> decode(byte[] body) {
        Message message = MessageBuilder.withBody(body).build();
        return decode(message);
    }

    /** Outbox 保存完整 JSON 信封，发布时再还原成同一个对象。 */
    public String toJson(RabbitMessageEnvelope<?> envelope) {
        validateEnvelope(envelope);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new MessageDecodingException("Outbox消息JSON序列化失败", e);
        }
    }

    public RabbitMessageEnvelope<JsonNode> fromJson(String json) {
        try {
            RabbitMessageEnvelope<JsonNode> envelope = objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructParametricType(RabbitMessageEnvelope.class, JsonNode.class));
            validateEnvelope(envelope);
            return envelope;
        } catch (Exception e) {
            throw new MessageDecodingException("Outbox消息JSON无法解析", e);
        }
    }

    public String toText(byte[] body) {
        return new String(body, StandardCharsets.UTF_8);
    }

    private void validateEnvelope(RabbitMessageEnvelope<?> envelope) {
        if (envelope == null || envelope.getMessageId() == null || envelope.getMessageId().isBlank()
                || envelope.getEventType() == null || envelope.getEventType().isBlank()
                || envelope.getSchemaVersion() == null || envelope.getOccurredAt() == null) {
            throw new MessageDecodingException("消息信封缺少 messageId、eventType、schemaVersion 或 occurredAt");
        }
    }
}
