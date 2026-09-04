package com.xt.xiaoxingxing.playground.features.designpatterns.creational;

import java.math.BigDecimal;

/** 抽象工厂模式：为同一支付渠道创建匹配的支付和退款产品。 */
public final class AbstractFactoryPatternDemo {

    private AbstractFactoryPatternDemo() {
    }

    public static void main(String[] args) {
        PaymentGateway directPayment = new WalletPaymentGateway();
        RefundGateway directRefund = new BankCardRefundGateway();
        System.out.println("直接拼装渠道一致：" + directPayment.channel().equals(directRefund.channel()));

        runFamily("钱包产品族", new WalletChannelFactory());
        runFamily("银行卡产品族", new BankCardChannelFactory());
    }

    private static void runFamily(String name, PaymentChannelFactory factory) {
        PaymentGateway paymentGateway = factory.createPaymentGateway();
        RefundGateway refundGateway = factory.createRefundGateway();

        System.out.println(name + "支付："
                + paymentGateway.pay("O-1001", new BigDecimal("100.00")));
        System.out.println(name + "退款："
                + refundGateway.refund("O-1001", new BigDecimal("20.00")));
        System.out.println(name + "渠道一致："
                + paymentGateway.channel().equals(refundGateway.channel()));
    }

    /** AbstractFactory：一次提供一整套相关产品。 */
    private interface PaymentChannelFactory {

        PaymentGateway createPaymentGateway();

        RefundGateway createRefundGateway();
    }

    private static final class WalletChannelFactory implements PaymentChannelFactory {

        @Override
        public PaymentGateway createPaymentGateway() {
            return new WalletPaymentGateway();
        }

        @Override
        public RefundGateway createRefundGateway() {
            return new WalletRefundGateway();
        }
    }

    private static final class BankCardChannelFactory implements PaymentChannelFactory {

        @Override
        public PaymentGateway createPaymentGateway() {
            return new BankCardPaymentGateway();
        }

        @Override
        public RefundGateway createRefundGateway() {
            return new BankCardRefundGateway();
        }
    }

    /** 两类 AbstractProduct。 */
    private interface PaymentGateway {

        String channel();

        String pay(String orderId, BigDecimal amount);
    }

    private interface RefundGateway {

        String channel();

        String refund(String orderId, BigDecimal amount);
    }

    private static final class WalletPaymentGateway implements PaymentGateway {

        @Override
        public String channel() {
            return "钱包";
        }

        @Override
        public String pay(String orderId, BigDecimal amount) {
            return channel() + "已支付 " + orderId + " / " + amount;
        }
    }

    private static final class WalletRefundGateway implements RefundGateway {

        @Override
        public String channel() {
            return "钱包";
        }

        @Override
        public String refund(String orderId, BigDecimal amount) {
            return channel() + "已退款 " + orderId + " / " + amount;
        }
    }

    private static final class BankCardPaymentGateway implements PaymentGateway {

        @Override
        public String channel() {
            return "银行卡";
        }

        @Override
        public String pay(String orderId, BigDecimal amount) {
            return channel() + "已支付 " + orderId + " / " + amount;
        }
    }

    private static final class BankCardRefundGateway implements RefundGateway {

        @Override
        public String channel() {
            return "银行卡";
        }

        @Override
        public String refund(String orderId, BigDecimal amount) {
            return channel() + "已退款 " + orderId + " / " + amount;
        }
    }
}
