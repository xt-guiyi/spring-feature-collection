package com.xt.xiaoxingxing.playground.rabbitmq.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** PostgreSQL mq_outbox_event 行对象。payload 保存完整的版本化消息信封 JSON。 */
@Data
public class MqOutboxEvent {

    private String id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private Integer schemaVersion;
    private String exchangeName;
    private String routingKey;
    private String payload;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lockedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
