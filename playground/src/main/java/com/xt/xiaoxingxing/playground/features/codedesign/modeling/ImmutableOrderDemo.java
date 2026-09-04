package com.xt.xiaoxingxing.playground.features.codedesign.modeling;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ImmutableOrderDemo {

    private ImmutableOrderDemo() {
    }

    public static void main(String[] args) {
        MutableOrder directOrder = new MutableOrder("ORDER-1001", new BigDecimal("100.00"));
        MutableOrder directBefore = directOrder;
        directOrder.applyDiscount(new BigDecimal("0.10"));

        OrderSnapshot improvedBefore = new OrderSnapshot("ORDER-1001", new BigDecimal("100.00"));
        OrderSnapshot improvedAfter = improvedBefore.applyDiscount(new BigDecimal("0.10"));

        System.out.println("直接写法折后金额：" + directOrder.amount());
        System.out.println("改进写法折后金额：" + improvedAfter.amount());
        System.out.println("业务结果一致："
                + (directOrder.amount().compareTo(improvedAfter.amount()) == 0));

        System.out.println("可变对象的旧引用也变成：" + directBefore.amount());
        System.out.println("不可变快照仍保留原金额：" + improvedBefore.amount());
    }

    private static BigDecimal discountedAmount(BigDecimal amount, BigDecimal discountRate) {
        return amount.multiply(BigDecimal.ONE.subtract(discountRate))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static final class MutableOrder {

        private final String orderId;
        private BigDecimal amount;

        private MutableOrder(String orderId, BigDecimal amount) {
            this.orderId = orderId;
            this.amount = amount;
        }

        private void applyDiscount(BigDecimal discountRate) {
            amount = discountedAmount(amount, discountRate);
        }

        private BigDecimal amount() {
            return amount;
        }
    }

    private record OrderSnapshot(String orderId, BigDecimal amount) {

        private OrderSnapshot applyDiscount(BigDecimal discountRate) {
            return new OrderSnapshot(orderId, discountedAmount(amount, discountRate));
        }
    }
}
