package com.xt.xiaoxingxing.playground.rabbitmq.enums;

/** ACK 行为学习接口支持的四种动作。 */
public enum RabbitAckAction {
    /** 业务成功后正常确认。 */
    ACK,
    /** 第一次否定确认并重新入队，第二次收到后 ACK，避免无限循环。 */
    NACK_REQUEUE_ONCE,
    /** 拒绝且不重新入队，由 DLX 转入最终死信队列。 */
    REJECT_TO_DEAD,
    /** 按 failTimes 注入失败，经过 TTL 重试队列后再成功或进入 DLQ。 */
    RETRY_THEN_SUCCESS
}
