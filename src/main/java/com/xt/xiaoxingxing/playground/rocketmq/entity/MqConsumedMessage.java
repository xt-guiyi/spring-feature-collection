package com.xt.xiaoxingxing.playground.rocketmq.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一个逻辑消费者已处理某个业务消息的持久化幂等凭证。
 *
 * <p>RocketMQ 按至少一次语义重试，{@code (consumer_name, message_id)} 唯一约束负责最终防重，
 * 但不同副作用的执行顺序并不相同：订单统计在同一个 PostgreSQL 事务中先插入本记录，只有首次插入者
 * 才继续 UPSERT；缓存删除无法与 PostgreSQL 组成同一事务，因此采用“短事务检查幂等记录、事务外删除
 * Redis 缓存、删除成功后登记本记录”。并发缓存消费可能重复执行天然幂等的 delete，唯一约束保证最终
 * 只留下一个完成凭证，后续投递即可直接跳过。</p>
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
