package com.xt.xiaoxingxing.playground.features.codedesign.modeling;

import java.math.BigDecimal;

public final class DomainBehaviorDemo {

    private DomainBehaviorDemo() {
    }

    public static void main(String[] args) {
        DirectOrder directOrder = new DirectOrder(new BigDecimal("99.00"));
        directOrder.setStatus(OrderStatus.PAID);

        Order improvedOrder = new Order(new BigDecimal("99.00"));
        improvedOrder.pay(new BigDecimal("99.00"));

        System.out.println("直接写法支付结果：" + directOrder.status());
        System.out.println("改进写法支付结果：" + improvedOrder.status());
        System.out.println("业务结果一致：" + (directOrder.status() == improvedOrder.status()));

        DirectOrder invalidDirectOrder = new DirectOrder(new BigDecimal("99.00"));
        invalidDirectOrder.setStatus(OrderStatus.SHIPPED);
        System.out.println("直接写法可绕过付款直接发货：" + invalidDirectOrder.status());

        try {
            new Order(new BigDecimal("99.00")).ship();
        } catch (IllegalStateException exception) {
            System.out.println("领域对象阻止非法发货：" + exception.getMessage());
        }
    }

    private enum OrderStatus {
        CREATED, PAID, SHIPPED
    }

    private static final class DirectOrder {

        private final BigDecimal amount;
        private OrderStatus status = OrderStatus.CREATED;

        private DirectOrder(BigDecimal amount) {
            this.amount = amount;
        }

        private void setStatus(OrderStatus status) {
            this.status = status;
        }

        private OrderStatus status() {
            return status;
        }
    }

    private static final class Order {

        private final BigDecimal amount;
        private OrderStatus status = OrderStatus.CREATED;

        private Order(BigDecimal amount) {
            this.amount = amount;
        }

        private void pay(BigDecimal receivedAmount) {
            if (status != OrderStatus.CREATED || amount.compareTo(receivedAmount) != 0) {
                throw new IllegalStateException("只有待支付订单且金额一致时才能付款");
            }
            status = OrderStatus.PAID;
        }

        private void ship() {
            if (status != OrderStatus.PAID) {
                throw new IllegalStateException("只有已支付订单才能发货");
            }
            status = OrderStatus.SHIPPED;
        }

        private OrderStatus status() {
            return status;
        }
    }
}
