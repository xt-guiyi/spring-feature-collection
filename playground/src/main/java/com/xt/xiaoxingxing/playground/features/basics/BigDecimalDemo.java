package com.xt.xiaoxingxing.playground.features.basics;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BigDecimalDemo {

    private BigDecimalDemo() {
    }

    public static void main(String[] args) {
        // 金额从字符串构造，避免浮点数带来的精度误差。
        BigDecimal price = new BigDecimal("19.90");
        BigDecimal quantity = new BigDecimal("3");
        BigDecimal total = price.multiply(quantity);
        BigDecimal discountedTotal = total.multiply(new BigDecimal("0.85"));
        BigDecimal payable = discountedTotal.setScale(2, RoundingMode.HALF_UP);

        System.out.println("原价总额：" + total);
        System.out.println("折后应付：" + payable);

        // 比较数值大小使用 compareTo，不受小数位数影响。
        boolean exceedsFifty = payable.compareTo(new BigDecimal("50.00")) > 0;
        System.out.println("是否超过 50 元：" + exceedsFifty);
    }
}
