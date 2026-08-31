package com.xt.xiaoxingxing.playground.drools.dto;

import com.xt.xiaoxingxing.playground.drools.fact.OrderRuleFact;
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

    private OrderRuleEvaluateResponse(OrderRuleFact fact, List<String> appliedRules) {
        this.orderNo = fact.getOrderNo();
        this.originalAmount = fact.getOriginalAmount();
        this.vip = fact.isVip();
        this.newUser = fact.isNewUser();
        this.discountRate = fact.getDiscountRate();
        this.shippingFee = fact.getShippingFee();
        this.finalAmount = fact.getFinalAmount();
        this.appliedRules = List.copyOf(appliedRules);
    }

    /** 根据规则事实和执行记录生成响应。 */
    public static OrderRuleEvaluateResponse from(OrderRuleFact fact, List<String> appliedRules) {
        return new OrderRuleEvaluateResponse(fact, appliedRules);
    }
}
