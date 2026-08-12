package com.xt.xiaoxingxing.playground.rocketmq.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import com.xt.xiaoxingxing.playground.rocketmq.message.RocketMessageEnvelope;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 统一负责“业务对象 ↔ JSON 信封”的协议边界。
 *
 * <p>统一使用项目的 ObjectMapper，而不是 Java 原生序列化。JSON 更容易跨语言、可被日志和 Dashboard 周边工具
 * 观察，也避免把 JVM 类名和实现细节固化为消息契约。</p>
 */
@Component
@RequiredArgsConstructor
public class RocketMessageCodec {

    private final ObjectMapper objectMapper;

    /**
     * 创建当前版本信封；messageId 在发送前产生，因此重复发布可保持同一个业务幂等键。
     *
     * <p>{@code ObjectMapper.valueToTree(null)} 返回的是表示 JSON {@code null} 的 {@link JsonNode}，
     * 而不是 Java {@code null}。因此构造完成后必须复用完整信封校验，避免空业务负载进入序列化或发布阶段。</p>
     */
    public RocketMessageEnvelope<JsonNode> newEnvelope(String eventType, String aggregateId, Object payload) {
        RocketMessageEnvelope<JsonNode> envelope = new RocketMessageEnvelope<>();
        envelope.setMessageId(UUID.randomUUID().toString());
        envelope.setEventType(eventType);
        envelope.setSchemaVersion(RocketMqNames.CURRENT_SCHEMA_VERSION);
        envelope.setAggregateId(aggregateId);
        envelope.setOccurredAt(LocalDateTime.now());
        envelope.setPayload(objectMapper.valueToTree(payload));
        validateEnvelope(envelope);
        return envelope;
    }

    /**
     * 从 RocketMQ 的只读 MessageView 解出版本化信封。
     *
     * <p>第1步：复制 ByteBuffer 的 remaining 字节；不能调用 {@code array()}，因为该缓冲区不保证有可访问的
     * 底层数组，也不保证 position 从 0 开始。第2步：反序列化 JSON。第3步：先校验必填协议字段，再拒绝
     * 不支持的版本。损坏 JSON 重试无法把字节修好，但消费者仍应返回 FAILURE 走有限次 Broker 重试，最终由
     * DLQ 收口，保留人工排查证据。</p>
     */
    public RocketMessageEnvelope<JsonNode> decode(MessageView messageView) {
        if (messageView == null) {
            throw new MessageDecodingException("RocketMQ MessageView不能为空");
        }
        ByteBuffer buffer = messageView.getBody();
        if (buffer == null) {
            throw new MessageDecodingException("RocketMQ消息体不能为空");
        }
        byte[] body = new byte[buffer.remaining()];
        buffer.get(body);
        return decodeBytes(body, "RocketMQ消息JSON无法解析");
    }

    /** Outbox 保存的是完整 JSON 信封，发布前还原后仍执行协议与版本校验。 */
    public RocketMessageEnvelope<JsonNode> fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new MessageDecodingException("Outbox消息JSON不能为空");
        }
        return decodeBytes(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), "Outbox消息JSON无法解析");
    }

    /** 将信封序列化为发布适配器和 Outbox 都可复用的 JSON 文本。 */
    public String toJson(RocketMessageEnvelope<?> envelope) {
        validateEnvelope(envelope);
        validateSchemaVersion(envelope);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new MessageDecodingException("RocketMQ消息JSON序列化失败", e);
        }
    }

    private RocketMessageEnvelope<JsonNode> decodeBytes(byte[] body, String errorMessage) {
        try {
            RocketMessageEnvelope<JsonNode> envelope = objectMapper.readValue(
                    body,
                    objectMapper.getTypeFactory().constructParametricType(RocketMessageEnvelope.class, JsonNode.class));
            validateEnvelope(envelope);
            validateSchemaVersion(envelope);
            return envelope;
        } catch (UnsupportedMessageVersionException e) {
            throw e;
        } catch (MessageDecodingException e) {
            throw e;
        } catch (Exception e) {
            throw new MessageDecodingException(errorMessage, e);
        }
    }

    private void validateEnvelope(RocketMessageEnvelope<?> envelope) {
        if (envelope == null
                || isBlank(envelope.getMessageId())
                || isBlank(envelope.getEventType())
                || envelope.getSchemaVersion() == null
                || isBlank(envelope.getAggregateId())
                || envelope.getOccurredAt() == null
                || isNullPayload(envelope.getPayload())) {
            throw new MessageDecodingException(
                    "消息信封缺少 messageId、eventType、schemaVersion、aggregateId、occurredAt 或 payload");
        }
    }

    /** Java null 与 Jackson 的 NullNode 都表示没有实际业务负载，协议层必须同等拒绝。 */
    private boolean isNullPayload(Object payload) {
        return payload == null || payload instanceof JsonNode node && node.isNull();
    }

    private void validateSchemaVersion(RocketMessageEnvelope<?> envelope) {
        if (envelope.getSchemaVersion() != RocketMqNames.CURRENT_SCHEMA_VERSION) {
            throw new UnsupportedMessageVersionException(
                    "不支持的消息版本: " + envelope.getSchemaVersion()
                            + "，当前仅支持: " + RocketMqNames.CURRENT_SCHEMA_VERSION);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
