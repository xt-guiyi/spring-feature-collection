package com.xt.xiaoxingxing.playground.rocketmq.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 消息消费记录。 */
@Data
public class MqConsumedMessage {

    private String consumerGroup;
    private String consumeId;
    private LocalDateTime consumedAt;
}
