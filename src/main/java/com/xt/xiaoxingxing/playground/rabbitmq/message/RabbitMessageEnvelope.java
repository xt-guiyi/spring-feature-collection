package com.xt.xiaoxingxing.playground.rabbitmq.message;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * RabbitMQ 业务消息的统一信封。
 *
 * <p>信封字段保持稳定，真正容易变化的业务数据放在 payload 中。消费者先读取 eventType 和 schemaVersion，
 * 再决定如何解释 payload，这比直接把某个 Java 实体序列化后长期传输更适合服务间演进。</p>
 */
@Data
public class RabbitMessageEnvelope<T> {

    /** 全局消息 ID：同时用于 Publisher Confirm 关联、日志定位和消费者幂等。 */
    private String messageId;

    /** 事件类型，例如 ORDER_CREATED、ORDER_PAID。 */
    private String eventType;

    /** 消息结构版本，不是数据库行版本，也不是订单状态版本。 */
    private Integer schemaVersion;

    /** 聚合根 ID；订单事件中保存 orderId 的字符串形式。 */
    private String aggregateId;

    /** 业务事件实际发生时间，而不是消费者收到消息的时间。 */
    private LocalDateTime occurredAt;

    /** 随事件类型变化的业务负载。 */
    private T payload;
}
