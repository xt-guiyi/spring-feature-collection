package com.xt.xiaoxingxing.playground.features.rocketmq.entity;

import lombok.Data;

import java.math.BigDecimal;

/** 订单明细。 */
@Data
public class OrderItem {

    private Long id;

    private Long orderId;

    private Long productId;

    private Integer quantity;

    private BigDecimal unitPrice;
}
