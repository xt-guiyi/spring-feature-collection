package com.xt.xiaoxingxing.playground.rocketmq.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * RocketMQ 事务消息的可持久化回查依据。
 *
 * <p>半消息发送前先写入 {@code PREPARED}；本地订单事务成功时与订单事实一起更新为
 * {@code COMMITTED}。Broker 回查只能以本记录和订单事实判断，绝不能依赖 JVM 内存状态。</p>
 */
@Data
public class MqTransactionRecord {

    /** 与半消息绑定的事务 ID，同时是本表主键。 */
    private String transactionId;
    /** 去重业务键；部分唯一索引阻止同一命令并发出现两条活跃事务链。 */
    private String businessKey;
    /** 独立、可重放的命令 JSON，而不是 HTTP 请求对象或内存引用。 */
    private String requestPayload;
    /** 只允许 PREPARED、COMMITTED、ROLLED_BACK 三种持久化终态/中间态。 */
    private String status;
    /** 本地事务提交后关联的订单主键；PREPARED 或回滚时允许为空。 */
    private Long orderId;
    /** 显式回滚或本地异常的简短原因，方便人工排查。 */
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
