package com.xt.xiaoxingxing.playground.postgresql.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderStatusUpdateRequest {

    private String newStatus;
    private String oldStatus;
    private Long userId;
    private BigDecimal minAmount;
}
