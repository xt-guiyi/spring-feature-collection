package com.xt.xiaoxingxing.playground.rocketmq.message;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * RocketMQ 业务消息的版本化统一信封。
 *
 * <p>信封字段是跨版本稳定的传输协议，容易演进的业务字段放进 {@code payload}。{@code schemaVersion}
 * 只表示这个 JSON 的解析规则，既不是数据库记录的乐观锁版本，也不是订单状态版本；三者混用会让升级与并发控制
 * 都难以判断。</p>
 */
@Data
public class RocketMessageEnvelope<T> {

    /** 生产前生成的稳定业务消息 ID；同一 Outbox 重试必须复用它以支撑消费幂等。 */
    private String messageId;

    /** 事件类型，例如 ORDER_CREATED；消费者依据它决定具体业务语义。 */
    private String eventType;

    /** JSON 信封和负载的协议版本。 */
    private Integer schemaVersion;

    /** 聚合根 ID；订单事件通常为 orderId，事务消息通常为 transactionId。 */
    private String aggregateId;

    /** 业务事件发生时间，不能用消费者实际收到消息的时间替代。 */
    private LocalDateTime occurredAt;

    /** 随 eventType 演进的业务数据。 */
    private T payload;
}
