package com.xt.xiaoxingxing.playground.rocketmq.message;

import lombok.Data;

import java.util.List;

/**
 * 事务消息本地事务所需的持久化命令快照。
 *
 * <p>事务回查可能在 HTTP 请求结束、原 JVM 重启之后发生，因此不能保存 Controller 请求对象或内存 Map；
 * transactionId 和商品明细必须可序列化并落入事务记录表。</p>
 */
@Data
public class TransactionOrderCommandPayload {

    private String transactionId;
    private Long userId;
    private List<TransactionOrderItemPayload> items;
}
