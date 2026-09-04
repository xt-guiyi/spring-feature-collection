package com.xt.xiaoxingxing.playground.features.codedesign.structure;

import java.math.BigDecimal;

public final class FeatureCohesionDemo {

    private FeatureCohesionDemo() {
    }

    public static void main(String[] args) {
        String orderId = "ORDER-1001";
        BigDecimal total = new BigDecimal("600.00");

        LegacyValidationUtils.requireValid(orderId, total);
        BigDecimal legacyPayable = LegacyMoneyUtils.vipPayable(total);
        String directResult = LegacyResponseUtils.format(orderId, legacyPayable);

        String improvedResult = OrderFeature.place(
                new OrderFeature.CreateOrderCommand(orderId, total, true)
        );

        System.out.println("按技术名称散落：" + directResult);
        System.out.println("按订单功能内聚：" + improvedResult);
    }

    private static final class LegacyValidationUtils {

        private static void requireValid(String orderId, BigDecimal total) {
            if (orderId.isBlank() || total.signum() <= 0) {
                throw new IllegalArgumentException("订单参数错误");
            }
        }
    }

    private static final class LegacyMoneyUtils {

        private static BigDecimal vipPayable(BigDecimal total) {
            return total.multiply(new BigDecimal("0.90"));
        }
    }

    private static final class LegacyResponseUtils {

        private static String format(String orderId, BigDecimal payable) {
            return orderId + " 应付 " + payable;
        }
    }

    // 内部类模拟 features/order 包：输入、规则和输出围绕订单功能放在一起。
    private static final class OrderFeature {

        private static String place(CreateOrderCommand command) {
            if (command.orderId().isBlank() || command.total().signum() <= 0) {
                throw new IllegalArgumentException("订单参数错误");
            }
            BigDecimal payable = command.vip()
                    ? command.total().multiply(new BigDecimal("0.90"))
                    : command.total();
            return command.orderId() + " 应付 " + payable;
        }

        private record CreateOrderCommand(String orderId, BigDecimal total, boolean vip) {
        }
    }
}
