package com.xt.xiaoxingxing.playground.features.designpatterns.behavioral;

import java.math.BigDecimal;

/** 策略模式：为同一运费计算目标替换不同算法。 */
public final class StrategyPatternDemo {

    private StrategyPatternDemo() {
    }

    public static void main(String[] args) {
        Order order = new Order(new BigDecimal("300.00"));
        ShippingFeeCalculator calculator = new ShippingFeeCalculator(new StandardShippingFee());
        compare(order, ShippingPlan.STANDARD, calculator);

        calculator.changeStrategy(new VipShippingFee());
        compare(order, ShippingPlan.VIP, calculator);
        calculator.changeStrategy(new RemoteAreaShippingFee());
        compare(order, ShippingPlan.REMOTE_AREA, calculator);
    }

    private static void compare(
            Order order,
            ShippingPlan plan,
            ShippingFeeCalculator calculator
    ) {
        BigDecimal directFee = calculateDirectly(order, plan);
        BigDecimal patternFee = calculator.calculate(order);
        System.out.println(plan + "：直接=" + directFee + "，策略=" + patternFee
                + "，一致=" + (directFee.compareTo(patternFee) == 0));
    }

    private static BigDecimal calculateDirectly(Order order, ShippingPlan plan) {
        return switch (plan) {
            case STANDARD -> order.amount().compareTo(new BigDecimal("500.00")) >= 0
                    ? BigDecimal.ZERO
                    : new BigDecimal("10.00");
            case VIP -> BigDecimal.ZERO;
            case REMOTE_AREA -> new BigDecimal("25.00");
        };
    }

    private interface ShippingFeeStrategy {

        BigDecimal calculate(Order order);
    }

    private static final class StandardShippingFee implements ShippingFeeStrategy {

        @Override
        public BigDecimal calculate(Order order) {
            return order.amount().compareTo(new BigDecimal("500.00")) >= 0
                    ? BigDecimal.ZERO
                    : new BigDecimal("10.00");
        }
    }

    private static final class VipShippingFee implements ShippingFeeStrategy {

        @Override
        public BigDecimal calculate(Order order) {
            return BigDecimal.ZERO;
        }
    }

    private static final class RemoteAreaShippingFee implements ShippingFeeStrategy {

        @Override
        public BigDecimal calculate(Order order) {
            return new BigDecimal("25.00");
        }
    }

    private static final class ShippingFeeCalculator {

        private ShippingFeeStrategy strategy;

        private ShippingFeeCalculator(ShippingFeeStrategy strategy) {
            this.strategy = strategy;
        }

        private void changeStrategy(ShippingFeeStrategy strategy) {
            this.strategy = strategy;
        }

        private BigDecimal calculate(Order order) {
            return strategy.calculate(order);
        }
    }

    private record Order(BigDecimal amount) {
    }

    private enum ShippingPlan {
        STANDARD, VIP, REMOTE_AREA
    }
}
