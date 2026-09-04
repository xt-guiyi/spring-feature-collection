package com.xt.xiaoxingxing.playground.features.codedesign.structure;

import java.math.BigDecimal;

public final class CompositionReuseDemo {

    private CompositionReuseDemo() {
    }

    public static void main(String[] args) {
        BigDecimal total = new BigDecimal("600.00");
        BigDecimal directPayable = new VipFreeShippingPrice().calculate(total);

        OrderCalculator calculator = new OrderCalculator(
                new DiscountCalculator(new BigDecimal("0.90")),
                new ShippingCalculator(BigDecimal.ZERO)
        );
        BigDecimal improvedPayable = calculator.calculate(total);

        System.out.println("继承复用的应付金额：" + directPayable);
        System.out.println("组合复用的应付金额：" + improvedPayable);
    }

    private static class BasicPrice {

        protected BigDecimal discounted(BigDecimal total) {
            return total;
        }

        protected BigDecimal shippingFee() {
            return new BigDecimal("20.00");
        }

        protected BigDecimal calculate(BigDecimal total) {
            return discounted(total).add(shippingFee());
        }
    }

    private static class VipPrice extends BasicPrice {

        @Override
        protected BigDecimal discounted(BigDecimal total) {
            return total.multiply(new BigDecimal("0.90"));
        }
    }

    private static final class VipFreeShippingPrice extends VipPrice {

        @Override
        protected BigDecimal shippingFee() {
            return BigDecimal.ZERO;
        }
    }

    private record DiscountCalculator(BigDecimal rate) {

        private BigDecimal apply(BigDecimal total) {
            return total.multiply(rate);
        }
    }

    private record ShippingCalculator(BigDecimal fee) {
    }

    private record OrderCalculator(
            DiscountCalculator discountCalculator,
            ShippingCalculator shippingCalculator
    ) {

        private BigDecimal calculate(BigDecimal total) {
            return discountCalculator.apply(total).add(shippingCalculator.fee());
        }
    }
}
