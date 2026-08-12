package com.xt.xiaoxingxing.playground.rocketmq.vo;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqNotificationLog;
import lombok.Data;

import java.time.LocalDateTime;

/** 模拟通知的运维响应；真实项目还应按权限隐藏手机号、内容等敏感信息。 */
@Data
public class RocketNotificationLogVO {

    private Long id;
    private String messageId;
    private Long orderId;
    private String eventType;
    private String channel;
    private String status;
    private String content;
    private LocalDateTime createdAt;

    public static RocketNotificationLogVO from(MqNotificationLog source) {
        if (source == null) {
            return null;
        }
        RocketNotificationLogVO target = new RocketNotificationLogVO();
        target.setId(source.getId());
        target.setMessageId(source.getMessageId());
        target.setOrderId(source.getOrderId());
        target.setEventType(source.getEventType());
        target.setChannel(source.getChannel());
        target.setStatus(source.getStatus());
        target.setContent(source.getContent());
        target.setCreatedAt(source.getCreatedAt());
        return target;
    }
}
