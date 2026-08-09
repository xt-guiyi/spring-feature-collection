package com.xt.xiaoxingxing.playground.rabbitmq.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 模拟短信或站内信的发送结果；真实项目应调用有幂等键能力的外部通知提供商。 */
@Data
public class MqNotificationLog {

    private Long id;
    private String messageId;
    private Long orderId;
    private String eventType;
    private String channel;
    private String status;
    private String content;
    private LocalDateTime createdAt;
}
