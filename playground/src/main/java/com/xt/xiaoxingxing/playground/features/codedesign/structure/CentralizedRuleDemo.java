package com.xt.xiaoxingxing.playground.features.codedesign.structure;

import java.math.BigDecimal;

public final class CentralizedRuleDemo {

    private CentralizedRuleDemo() {
    }

    public static void main(String[] args) {
        BigDecimal total = new BigDecimal("600.00");
        BigDecimal directPayable = directCalculate(total, true);

        OrderRules rules = new OrderRules(
                new BigDecimal("500.00"),
                new BigDecimal("0.90"),
                new BigDecimal("300.00"),
                new BigDecimal("20.00")
        );
        BigDecimal improvedPayable = rules.calculatePayable(total, true);

        System.out.println("规则散落时的应付金额：" + directPayable);
        System.out.println("规则集中后的应付金额：" + improvedPayable);
    }

    private static BigDecimal directCalculate(BigDecimal total, boolean vip) {
        BigDecimal discounted = total;
        // 阈值、折扣率和运费散落在流程代码中，修改规则时容易遗漏。
        if (vip && total.compareTo(new BigDecimal("500.00")) >= 0) {
            discounted = total.multiply(new BigDecimal("0.90"));
        }
        BigDecimal shippingFee = total.compareTo(new BigDecimal("300.00")) >= 0
                ? BigDecimal.ZERO
                : new BigDecimal("20.00");
        return discounted.add(shippingFee);
    }

    private record OrderRules(
            BigDecimal vipThreshold,
            BigDecimal vipRate,
            BigDecimal freeShippingThreshold,
            BigDecimal shippingFee
    ) {

        private BigDecimal calculatePayable(BigDecimal total, boolean vip) {
            BigDecimal discounted = vip && total.compareTo(vipThreshold) >= 0
                    ? total.multiply(vipRate)
                    : total;
            BigDecimal actualShippingFee = total.compareTo(freeShippingThreshold) >= 0
                    ? BigDecimal.ZERO
                    : shippingFee;
            return discounted.add(actualShippingFee);
        }
    }
}
