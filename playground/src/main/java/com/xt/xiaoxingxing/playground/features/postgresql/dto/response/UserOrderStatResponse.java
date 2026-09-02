package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserOrderStatResponse {

    private Long userId;
    private String username;
    private Long orderCount;
    private BigDecimal totalSpent;
}
