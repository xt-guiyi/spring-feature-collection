package com.xt.xiaoxingxing.playground.postgresql.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserOrderStatVO {

    private Long userId;
    private String username;
    private Long orderCount;
    private BigDecimal totalSpent;
}
