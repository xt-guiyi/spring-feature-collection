package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDetailResponse {

    private Long orderId;
    private String orderNo;
    private String username;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
