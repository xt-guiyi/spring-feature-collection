package com.xt.xiaoxingxing.playground.rabbitmq.vo;

import com.xt.xiaoxingxing.playground.rabbitmq.support.RabbitPublishResult;
import lombok.Data;

/** HTTP 层可读的发布结果，不直接返回 Spring AMQP 的框架对象。 */
@Data
public class RabbitMessagePublishVO {

    private boolean success;
    private String messageId;
    private String exchange;
    private String routingKey;
    private String reason;

    public static RabbitMessagePublishVO from(RabbitPublishResult result) {
        RabbitMessagePublishVO vo = new RabbitMessagePublishVO();
        vo.setSuccess(result.isSuccess());
        vo.setMessageId(result.getMessageId());
        vo.setExchange(result.getExchange());
        vo.setRoutingKey(result.getRoutingKey());
        vo.setReason(result.getReason());
        return vo;
    }
}
