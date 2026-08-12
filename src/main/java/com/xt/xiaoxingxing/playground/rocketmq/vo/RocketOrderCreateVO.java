package com.xt.xiaoxingxing.playground.rocketmq.vo;

import lombok.Data;

import java.math.BigDecimal;

/** Outbox 创建订单的可观察结果；mechanism 明确告诉调用者这条链路没有走事务消息。 */
@Data
public class RocketOrderCreateVO {

    private String mechanism;
    private Long orderId;
    private String orderNo;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private String orderCreatedMessageId;
    private String paymentTimeoutMessageId;
}
