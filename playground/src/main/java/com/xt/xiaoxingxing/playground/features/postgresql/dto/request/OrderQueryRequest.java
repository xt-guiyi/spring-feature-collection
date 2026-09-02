package com.xt.xiaoxingxing.playground.features.postgresql.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderQueryRequest {

    private Long userId;
    private String status;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
}
