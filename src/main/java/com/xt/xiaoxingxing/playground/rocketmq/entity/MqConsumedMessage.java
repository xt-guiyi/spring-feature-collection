package com.xt.xiaoxingxing.playground.rocketmq.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一个逻辑消费者已处理某个业务消息的持久化幂等凭证。
 *
 * <p>RocketMQ 按至少一次语义重试；同一消费者的并发副本竞争
 * {@code (consumer_name, message_id)} 唯一约束，只有插入成功的一方继续执行业务。</p>
 */
@Data
public class MqConsumedMessage {

    private Long id;
    /** 稳定消费者名称，而不是临时实例 ID；它对应幂等边界。 */
    private String consumerName;
    /** 版本化消息信封的业务 messageId。 */
    private String messageId;
    private String eventType;
    private String aggregateId;
    private LocalDateTime consumedAt;
}
