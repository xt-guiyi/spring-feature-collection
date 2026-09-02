package com.xt.xiaoxingxing.playground.features.rocketmq.dto.response;

import lombok.Getter;

import java.math.BigDecimal;

/** 订单响应。 */
@Getter
public final class OrderResponse {

    private final Long orderId;
    private final String orderNo;
    private final String status;
    private final BigDecimal totalAmount;
    private final Integer itemCount;

    public OrderResponse(Long orderId,
                         String orderNo,
                         String status,
                         BigDecimal totalAmount,
                         Integer itemCount) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.status = status;
        this.totalAmount = totalAmount;
        this.itemCount = itemCount;
    }

}
