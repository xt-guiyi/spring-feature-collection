package com.xt.xiaoxingxing.playground.rabbitmq.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 创建订单后同时返回 Outbox ID，方便直接去数据库观察两条待发布事件。 */
@Data
public class RabbitOrderCreateVO {

    private Long orderId;
    private String orderNo;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private String orderCreatedMessageId;
    private String paymentTimeoutMessageId;
}
