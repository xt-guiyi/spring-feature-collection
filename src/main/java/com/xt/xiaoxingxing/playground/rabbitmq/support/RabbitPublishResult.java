package com.xt.xiaoxingxing.playground.rabbitmq.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Publisher Confirm 与 Mandatory Return 合并后的发布结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RabbitPublishResult {

    private boolean success;
    private String messageId;
    private String exchange;
    private String routingKey;
    private String reason;

    public static RabbitPublishResult success(String messageId, String exchange, String routingKey) {
        return new RabbitPublishResult(true, messageId, exchange, routingKey, "Broker已确认且消息没有被退回");
    }

    public static RabbitPublishResult failure(String messageId, String exchange, String routingKey, String reason) {
        return new RabbitPublishResult(false, messageId, exchange, routingKey, reason);
    }
}
