package com.xt.xiaoxingxing.playground.drools.fact;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 交给 Drools 进行规则匹配的订单事实（Fact）。
 *
 * <p>它是 Drools 学习用模型，不复用 RocketMQ 模块中的持久化订单实体。</p>
 */
@Getter
public class OrderRuleFact {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("500.00");
    private static final BigDecimal DEFAULT_SHIPPING_FEE = new BigDecimal("20.00");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final String orderNo;
    private final BigDecimal originalAmount;
    private final boolean vip;
    private final boolean newUser;

    private BigDecimal discountRate = BigDecimal.ONE.setScale(2);
    private BigDecimal shippingFee = DEFAULT_SHIPPING_FEE;
    private BigDecimal finalAmount;

    public OrderRuleFact(String orderNo, BigDecimal originalAmount, boolean vip, boolean newUser) {
        this.orderNo = orderNo;
        this.originalAmount = originalAmount.setScale(2, RoundingMode.HALF_UP);
        this.vip = vip;
        this.newUser = newUser;
    }

    /** 订单金额是否达到大额订单门槛。 */
    public boolean isHighValue() {
        return originalAmount.compareTo(HIGH_VALUE_THRESHOLD) >= 0;
    }

    /** 是否尚未应用折扣。 */
    public boolean isDiscountApplied() {
        return discountRate.compareTo(BigDecimal.ONE) != 0;
    }

    /** 是否仍在收取运费。 */
    public boolean isShippingFeeCharged() {
        return shippingFee.compareTo(ZERO) > 0;
    }

    /** 根据当前规则结果计算最终金额，供最终计算规则调用。 */
    public BigDecimal calculateFinalAmount() {
        return originalAmount
                .multiply(discountRate)
                .add(shippingFee)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public void setDiscountRate(BigDecimal discountRate) {
        this.discountRate = discountRate.setScale(2, RoundingMode.HALF_UP);
    }

    public void setShippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee.setScale(2, RoundingMode.HALF_UP);
    }

    public void setFinalAmount(BigDecimal finalAmount) {
        this.finalAmount = finalAmount == null
                ? null
                : finalAmount.setScale(2, RoundingMode.HALF_UP);
    }
}
