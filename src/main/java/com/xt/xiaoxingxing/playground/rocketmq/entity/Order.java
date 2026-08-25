package com.xt.xiaoxingxing.playground.rocketmq.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单。 */
@Data
public class Order {

    private Long id;

    private Long userId;

    private String orderNo;

    private BigDecimal totalAmount;

    private String status;

    private LocalDateTime createdAt;
}
