package com.xt.xiaoxingxing.playground.rocketmq.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
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
 */
@Component
@RequiredArgsConstructor
public class RocketMessageCodec {

    /** Spring Boot 4.1 自动配置的 Jackson 3 JsonMapper，同时服务 HTTP、缓存和消息信封。 */
    private final JsonMapper jsonMapper;

    /**
     * 创建当前版本信封；messageId 在发送前产生，因此重复发布可保持同一个业务幂等键。
     *
     * <p>{@code JsonMapper.valueToTree(null)} 返回的是表示 JSON {@code null} 的 {@link JsonNode}，
     * 而不是 Java {@code null}。因此构造完成后必须复用完整信封校验，避免空业务负载进入序列化或发布阶段。</p>
     */
    public RocketMessageEnvelope<JsonNode> newEnvelope(String eventType, String aggregateId, Object payload) {
        return newEnvelope(UUID.randomUUID().toString(), eventType, aggregateId, payload);
    }

    /**
     * 使用调用方提供的稳定 messageId 创建信封。
     *
     * <p>这个重载用于需要预先确定幂等键的可靠消息，例如订单创建事务同时写入“订单事件”和
     * “延迟超时检查”时，可以先根据订单业务键生成稳定 ID，再把它保存进 Outbox。之后无论调度器
     * 重试多少次，都恢复并发送同一个 messageId，而不是每次重试生成一个新 ID。</p>
     */
    public RocketMessageEnvelope<JsonNode> newEnvelope(String messageId,
                                                       String eventType,
                                                       String aggregateId,
                                                       Object payload) {
        RocketMessageEnvelope<JsonNode> envelope = new RocketMessageEnvelope<>();
        envelope.setMessageId(messageId);
        envelope.setEventType(eventType);
        envelope.setSchemaVersion(RocketMqNames.CURRENT_SCHEMA_VERSION);
        envelope.setAggregateId(aggregateId);
        envelope.setOccurredAt(LocalDateTime.now());
        envelope.setPayload(jsonMapper.valueToTree(payload));
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
            return jsonMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new MessageDecodingException("RocketMQ消息JSON序列化失败", e);
        }
    }

    private RocketMessageEnvelope<JsonNode> decodeBytes(byte[] body, String errorMessage) {
        try {
            RocketMessageEnvelope<JsonNode> envelope = jsonMapper.readValue(
                    body,
                    jsonMapper.getTypeFactory().constructParametricType(RocketMessageEnvelope.class, JsonNode.class));
            validateEnvelope(envelope);
            validateSchemaVersion(envelope);
            return envelope;
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
            throw new MessageDecodingException(
                    "不支持的消息版本: " + envelope.getSchemaVersion()
                            + "，当前仅支持: " + RocketMqNames.CURRENT_SCHEMA_VERSION);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
