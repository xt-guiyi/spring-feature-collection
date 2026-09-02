package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserRankResponse {

    private Long userId;
    private String username;
    private BigDecimal totalSpent;
    private Long rank;
}
