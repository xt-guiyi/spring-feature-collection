package com.xt.xiaoxingxing.playground.rabbitmq.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 某一个逻辑消费者已经处理过某一 messageId 的持久化凭证。 */
@Data
public class MqConsumedMessage {

    private Long id;
    private String consumerName;
    private String messageId;
    private String eventType;
    private String aggregateId;
    private LocalDateTime consumedAt;
}
