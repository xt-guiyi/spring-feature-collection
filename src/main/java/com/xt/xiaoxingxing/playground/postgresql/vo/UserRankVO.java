package com.xt.xiaoxingxing.playground.postgresql.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserRankVO {

    private Long userId;
    private String username;
    private BigDecimal totalSpent;
    private Long rank;
}
