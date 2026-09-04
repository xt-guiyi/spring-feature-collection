package com.xt.xiaoxingxing.playground.features.designpatterns.behavioral;

/** 责任链：把下单校验串成可扩展、可中止的处理链。 */
public final class ChainOfResponsibilityPatternDemo {

    private ChainOfResponsibilityPatternDemo() {
    }

    public static void main(String[] args) {
        Order order = new Order("O-1001", "上海市浦东新区", 200, 8, 500, false);

        CheckResult directResult = checkDirectly(order);
        OrderCheck chain = new AddressCheck(
                new StockCheck(new CreditCheck(new RiskCheck(null))));
        CheckResult patternResult = chain.check(order);

        System.out.println("直接写法：" + directResult);
        System.out.println("责任链写法：" + patternResult);
        System.out.println("业务结果一致：" + directResult.equals(patternResult));

        Order riskyOrder = new Order("O-1002", "上海市浦东新区", 200, 8, 500, true);
        System.out.println("新增风控节点后中止：" + chain.check(riskyOrder));
    }

    private static CheckResult checkDirectly(Order order) {
        if (order.address() == null || order.address().isBlank()) {
            return CheckResult.reject("收货地址不能为空");
        }
        if (order.stock() <= 0) {
            return CheckResult.reject("库存不足");
        }
        if (order.amount() > order.creditLimit()) {
            return CheckResult.reject("超过客户额度");
        }
        if (order.highRisk()) {
            return CheckResult.reject("订单命中风控");
        }
        return CheckResult.pass();
    }

    private abstract static class OrderCheck {

        private final OrderCheck next;

        private OrderCheck(OrderCheck next) {
            this.next = next;
        }

        private CheckResult check(Order order) {
            CheckResult current = checkCurrent(order);
            if (!current.passed() || next == null) {
                return current;
            }
            return next.check(order);
        }

        protected abstract CheckResult checkCurrent(Order order);
    }

    private static final class AddressCheck extends OrderCheck {

        private AddressCheck(OrderCheck next) {
            super(next);
        }

        @Override
        protected CheckResult checkCurrent(Order order) {
            return order.address() != null && !order.address().isBlank()
                    ? CheckResult.pass()
                    : CheckResult.reject("收货地址不能为空");
        }
    }

    private static final class StockCheck extends OrderCheck {

        private StockCheck(OrderCheck next) {
            super(next);
        }

        @Override
        protected CheckResult checkCurrent(Order order) {
            return order.stock() > 0 ? CheckResult.pass() : CheckResult.reject("库存不足");
        }
    }

    private static final class CreditCheck extends OrderCheck {

        private CreditCheck(OrderCheck next) {
            super(next);
        }

        @Override
        protected CheckResult checkCurrent(Order order) {
            return order.amount() <= order.creditLimit()
                    ? CheckResult.pass()
                    : CheckResult.reject("超过客户额度");
        }
    }

    private static final class RiskCheck extends OrderCheck {

        private RiskCheck(OrderCheck next) {
            super(next);
        }

        @Override
        protected CheckResult checkCurrent(Order order) {
            return order.highRisk() ? CheckResult.reject("订单命中风控") : CheckResult.pass();
        }
    }

    private record Order(
            String orderNo,
            String address,
            int amount,
            int stock,
            int creditLimit,
            boolean highRisk
    ) {
    }

    private record CheckResult(boolean passed, String message) {

        private static CheckResult pass() {
            return new CheckResult(true, "允许下单");
        }

        private static CheckResult reject(String message) {
            return new CheckResult(false, message);
        }
    }
}
