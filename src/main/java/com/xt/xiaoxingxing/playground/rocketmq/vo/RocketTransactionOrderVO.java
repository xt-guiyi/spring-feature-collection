package com.xt.xiaoxingxing.playground.rocketmq.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * RocketMQ 事务消息创建订单的结果。
 *
 * <p>transactionState 为 UNKNOWN 时不表示订单必然失败，而是调用方应等待 Broker 根据持久化事务记录回查。
 * 该响应刻意不暴露 Transaction 等框架对象，避免 HTTP 层误操作提交或回滚。</p>
 */
@Data
public class RocketTransactionOrderVO {

    private String mechanism;
    private String transactionId;
    private String businessKey;
    private Long orderId;
    private String orderNo;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private String brokerMessageId;
    private String transactionState;
    private String message;
}
