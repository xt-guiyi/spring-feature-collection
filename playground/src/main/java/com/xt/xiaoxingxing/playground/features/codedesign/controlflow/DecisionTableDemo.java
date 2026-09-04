package com.xt.xiaoxingxing.playground.features.codedesign.controlflow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** 决策表：集中展示客户等级和订单金额共同决定的折扣。 */
public final class DecisionTableDemo {

    private static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("500.00");
    private static final Map<DecisionKey, BigDecimal> DISCOUNT_TABLE = Map.of(
            new DecisionKey(CustomerLevel.REGULAR, AmountBand.NORMAL), new BigDecimal("1.00"),
            new DecisionKey(CustomerLevel.REGULAR, AmountBand.LARGE), new BigDecimal("0.97"),
            new DecisionKey(CustomerLevel.VIP, AmountBand.NORMAL), new BigDecimal("0.95"),
            new DecisionKey(CustomerLevel.VIP, AmountBand.LARGE), new BigDecimal("0.90"));

    private DecisionTableDemo() {
    }

    public static void main(String[] args) {
        Order order = new Order(CustomerLevel.VIP, new BigDecimal("680.00"));

        BigDecimal directAmount = payable(order.amount(), directRate(order));
        BigDecimal improvedAmount = payable(order.amount(), tableRate(order));
        System.out.println("直接写法结果：" + directAmount);
        System.out.println("改进写法结果：" + improvedAmount);
    }

    private static BigDecimal directRate(Order order) {
        if (order.level() == CustomerLevel.VIP && isLarge(order.amount())) {
            return new BigDecimal("0.90");
        }
        if (order.level() == CustomerLevel.VIP) {
            return new BigDecimal("0.95");
        }
        if (isLarge(order.amount())) {
            return new BigDecimal("0.97");
        }
        return BigDecimal.ONE;
    }

    private static BigDecimal tableRate(Order order) {
        AmountBand band = isLarge(order.amount()) ? AmountBand.LARGE : AmountBand.NORMAL;
        return DISCOUNT_TABLE.get(new DecisionKey(order.level(), band));
    }

    private static boolean isLarge(BigDecimal amount) {
        return amount.compareTo(LARGE_ORDER_THRESHOLD) >= 0;
    }

    private static BigDecimal payable(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private record Order(CustomerLevel level, BigDecimal amount) {
    }

    private record DecisionKey(CustomerLevel level, AmountBand band) {
    }

    private enum CustomerLevel {
        REGULAR, VIP
    }

    private enum AmountBand {
        NORMAL, LARGE
    }
}
