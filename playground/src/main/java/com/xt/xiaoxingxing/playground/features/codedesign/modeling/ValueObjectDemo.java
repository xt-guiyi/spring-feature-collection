package com.xt.xiaoxingxing.playground.features.codedesign.modeling;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class ValueObjectDemo {

    private ValueObjectDemo() {
    }

    public static void main(String[] args) {
        String orderId = "ORDER-1001";
        BigDecimal amount = new BigDecimal("89.90");

        String directResult = directSummary(orderId, amount);
        String improvedResult = improvedSummary(new OrderId(orderId), new Money(amount));

        System.out.println("直接写法：" + directResult);
        System.out.println("改进写法：" + improvedResult);
        System.out.println("业务结果一致：" + directResult.equals(improvedResult));

        try {
            new Money(new BigDecimal("-1.00"));
        } catch (IllegalArgumentException exception) {
            System.out.println("值对象阻止非法金额：" + exception.getMessage());
        }
    }

    private static String directSummary(String orderId, BigDecimal amount) {
        return "订单 %s，应付 %s 元".formatted(orderId, amount.setScale(2, RoundingMode.HALF_UP));
    }

    private static String improvedSummary(OrderId orderId, Money amount) {
        return "订单 %s，应付 %s 元".formatted(orderId.value(), amount.value());
    }

    private record OrderId(String value) {

        private OrderId {
            Objects.requireNonNull(value, "订单编号不能为空");
            if (value.isBlank()) {
                throw new IllegalArgumentException("订单编号不能为空");
            }
        }
    }

    private record Money(BigDecimal value) {

        private Money {
            Objects.requireNonNull(value, "金额不能为空");
            if (value.signum() < 0) {
                throw new IllegalArgumentException("金额不能为负数");
            }
            value = value.setScale(2, RoundingMode.HALF_UP);
        }
    }
}
