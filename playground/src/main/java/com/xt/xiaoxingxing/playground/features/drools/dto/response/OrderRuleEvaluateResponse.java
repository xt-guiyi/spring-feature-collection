package com.xt.xiaoxingxing.playground.features.drools.dto.response;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/** 订单规则计算结果。 */
@Getter
public class OrderRuleEvaluateResponse {

    private final String orderNo;
    private final BigDecimal originalAmount;
    private final boolean vip;
    private final boolean newUser;
    private final BigDecimal discountRate;
    private final BigDecimal shippingFee;
    private final BigDecimal finalAmount;
    private final List<String> appliedRules;

    public OrderRuleEvaluateResponse(String orderNo,
                                     BigDecimal originalAmount,
                                     boolean vip,
                                     boolean newUser,
                                     BigDecimal discountRate,
                                     BigDecimal shippingFee,
                                     BigDecimal finalAmount,
                                     List<String> appliedRules) {
        this.orderNo = orderNo;
        this.originalAmount = originalAmount;
        this.vip = vip;
        this.newUser = newUser;
        this.discountRate = discountRate;
        this.shippingFee = shippingFee;
        this.finalAmount = finalAmount;
        this.appliedRules = List.copyOf(appliedRules);
    }

}
