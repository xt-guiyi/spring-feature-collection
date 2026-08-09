package com.xt.xiaoxingxing.playground.postgresql.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserOrderVO {

    private Long userId;
    private String username;
    private String orderNo;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime orderTime;
}
