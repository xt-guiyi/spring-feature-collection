package com.xt.xiaoxingxing.playground.rocketmq.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模拟短信或站内信的发送记录。
 *
 * <p>真实项目还应把本地唯一键传给具备幂等能力的外部通知服务；本表的
 * {@code (message_id, channel)} 仅保证本地不会重复发起同一渠道的业务动作。</p>
 */
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
