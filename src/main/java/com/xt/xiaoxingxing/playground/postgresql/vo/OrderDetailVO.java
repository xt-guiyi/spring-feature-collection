package com.xt.xiaoxingxing.playground.postgresql.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDetailVO {

    private Long orderId;
    private String orderNo;
    private String username;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
