package com.xt.xiaoxingxing.playground.features.designpatterns.creational;

import java.math.BigDecimal;

/** 工厂方法模式：由 Creator 子类决定创建哪一种支付网关。 */
public final class FactoryMethodPatternDemo {

    private FactoryMethodPatternDemo() {
    }

    public static void main(String[] args) {
        OrderPayment payment = new OrderPayment("O-1001", new BigDecimal("100.00"));

        PaymentReceipt direct = directCreate(PaymentChannel.WALLET).pay(payment);
        System.out.println("直接写法：" + direct.summary());

        PaymentGatewayCreator walletCreator = new WalletGatewayCreator();
        PaymentReceipt created = walletCreator.createAndPay(payment);
        System.out.println("工厂方法：" + created.summary());
        System.out.println("两种写法结果相同：" + direct.equals(created));

        PaymentGatewayCreator bankCardCreator = new BankCardGatewayCreator();
        System.out.println("扩展银行卡渠道：" + bankCardCreator.createAndPay(payment).summary());
    }

    /** 直接写法：每增加渠道都要修改这个集中判断。 */
    private static PaymentGateway directCreate(PaymentChannel channel) {
        return switch (channel) {
            case WALLET -> new WalletGateway();
            case BANK_CARD -> new BankCardGateway();
        };
    }

    /** Creator：业务流程使用工厂方法得到产品。 */
    private abstract static class PaymentGatewayCreator {

        private PaymentReceipt createAndPay(OrderPayment payment) {
            return createGateway().pay(payment);
        }

        protected abstract PaymentGateway createGateway();
    }

    private static final class WalletGatewayCreator extends PaymentGatewayCreator {

        @Override
        protected PaymentGateway createGateway() {
            return new WalletGateway();
        }
    }

    private static final class BankCardGatewayCreator extends PaymentGatewayCreator {

        @Override
        protected PaymentGateway createGateway() {
            return new BankCardGateway();
        }
    }

    /** Product。 */
    private interface PaymentGateway {

        PaymentReceipt pay(OrderPayment payment);
    }

    private static final class WalletGateway implements PaymentGateway {

        @Override
        public PaymentReceipt pay(OrderPayment payment) {
            return new PaymentReceipt(payment.orderId(), "钱包", payment.amount());
        }
    }

    private static final class BankCardGateway implements PaymentGateway {

        @Override
        public PaymentReceipt pay(OrderPayment payment) {
            return new PaymentReceipt(payment.orderId(), "银行卡", payment.amount());
        }
    }

    private record OrderPayment(String orderId, BigDecimal amount) {
    }

    private record PaymentReceipt(String orderId, String channel, BigDecimal amount) {

        private String summary() {
            return channel + "支付订单 " + orderId + "，金额 " + amount;
        }
    }

    private enum PaymentChannel {
        WALLET, BANK_CARD
    }
}
