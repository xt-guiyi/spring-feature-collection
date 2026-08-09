package com.xt.xiaoxingxing.playground.postgresql.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 创建完整订单成功后的摘要响应。 */
@Data
public class CompleteOrderResponse {

    private Long orderId;
    private String orderNo;
    private BigDecimal totalAmount;
    private Integer itemCount;
}
