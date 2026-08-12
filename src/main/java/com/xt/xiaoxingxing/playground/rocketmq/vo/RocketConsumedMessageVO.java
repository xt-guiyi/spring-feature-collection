package com.xt.xiaoxingxing.playground.rocketmq.vo;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqConsumedMessage;
import lombok.Data;

import java.time.LocalDateTime;

/** 消费幂等记录响应，用于观察某个 ConsumerGroup 是否已经领取过业务消息。 */
@Data
public class RocketConsumedMessageVO {

    private Long id;
    private String consumerName;
    private String messageId;
    private String eventType;
    private String aggregateId;
    private LocalDateTime consumedAt;

    public static RocketConsumedMessageVO from(MqConsumedMessage source) {
        if (source == null) {
            return null;
        }
        RocketConsumedMessageVO target = new RocketConsumedMessageVO();
        target.setId(source.getId());
        target.setConsumerName(source.getConsumerName());
        target.setMessageId(source.getMessageId());
        target.setEventType(source.getEventType());
        target.setAggregateId(source.getAggregateId());
        target.setConsumedAt(source.getConsumedAt());
        return target;
    }
}
