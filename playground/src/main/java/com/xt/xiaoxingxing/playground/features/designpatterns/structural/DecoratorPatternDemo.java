package com.xt.xiaoxingxing.playground.features.designpatterns.structural;

import java.math.BigDecimal;

/** 装饰器：保持报价接口不变，按需要逐层叠加费用。 */
public final class DecoratorPatternDemo {

    private DecoratorPatternDemo() {
    }

    public static void main(String[] args) {
        BigDecimal directTotal = new BigDecimal("100.00")
                .add(new BigDecimal("10.00"))
                .add(new BigDecimal("5.00"))
                .add(new BigDecimal("2.00"));

        Quote standardQuote = new InsuranceDecorator(
                new ShippingDecorator(
                        new ProductQuote(new BigDecimal("100.00")),
                        new BigDecimal("10.00")
                ),
                new BigDecimal("5.00")
        );
        Quote giftQuote = new GiftWrapDecorator(standardQuote, new BigDecimal("2.00"));

        System.out.println("直接写法：应付 " + directTotal);
        System.out.println("装饰器写法：应付 " + giftQuote.total());
        System.out.println("新增礼盒包装无需修改原报价：" + giftQuote.description());
    }

    private interface Quote {

        BigDecimal total();

        String description();
    }

    private record ProductQuote(BigDecimal total) implements Quote {

        @Override
        public String description() {
            return "商品";
        }
    }

    private abstract static class QuoteDecorator implements Quote {

        private final Quote delegate;

        private QuoteDecorator(Quote delegate) {
            this.delegate = delegate;
        }

        protected final Quote delegate() {
            return delegate;
        }
    }

    private static final class ShippingDecorator extends QuoteDecorator {

        private final BigDecimal fee;

        private ShippingDecorator(Quote delegate, BigDecimal fee) {
            super(delegate);
            this.fee = fee;
        }

        @Override
        public BigDecimal total() {
            return delegate().total().add(fee);
        }

        @Override
        public String description() {
            return delegate().description() + " + 运费";
        }
    }

    private static final class InsuranceDecorator extends QuoteDecorator {

        private final BigDecimal fee;

        private InsuranceDecorator(Quote delegate, BigDecimal fee) {
            super(delegate);
            this.fee = fee;
        }

        @Override
        public BigDecimal total() {
            return delegate().total().add(fee);
        }

        @Override
        public String description() {
            return delegate().description() + " + 保价";
        }
    }

    private static final class GiftWrapDecorator extends QuoteDecorator {

        private final BigDecimal fee;

        private GiftWrapDecorator(Quote delegate, BigDecimal fee) {
            super(delegate);
            this.fee = fee;
        }

        @Override
        public BigDecimal total() {
            return delegate().total().add(fee);
        }

        @Override
        public String description() {
            return delegate().description() + " + 礼盒包装";
        }
    }
}
