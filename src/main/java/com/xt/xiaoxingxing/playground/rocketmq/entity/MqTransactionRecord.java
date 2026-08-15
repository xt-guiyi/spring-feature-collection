package com.xt.xiaoxingxing.playground.rocketmq.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * RocketMQ 事务消息的通用持久化回查依据。
 *
 * <p>半消息发送前先写入 {@code PREPARED}；本地业务事务成功时与业务事实一起更新为
 * {@code COMMITTED}。记录只保存事务协调需要的通用业务标识，不出现订单 ID、订单号等领域专用字段，
 * 因而后续库存、账户、优惠券等业务也可以复用同一套回查基础设施。</p>
 */
@Data
public class MqTransactionRecord {

    /** 与半消息绑定的事务 ID，同时是本表主键和信封 messageId。 */
    private String transactionId;
    /** 业务类型，例如 ORDER、INVENTORY；具体合法值由调用方业务协议约束。 */
    private String businessType;
    /** 稳定业务键，例如订单号、退款单号或库存预占号；与业务类型、操作类型共同形成活跃唯一键。 */
    private String businessKey;
    /** 本地业务操作类型，例如 CREATE、PAY、CANCEL；基础设施不限制具体枚举。 */
    private String operationType;
    /** 只允许 PREPARED、COMMITTED、ROLLED_BACK 三种持久化终态/中间态。 */
    private String status;
    /** 显式回滚或本地异常的简短原因，方便人工排查。 */
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
