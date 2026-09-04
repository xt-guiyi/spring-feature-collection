package com.xt.xiaoxingxing.playground.features.codedesign.controlflow;

import java.math.BigDecimal;

/** 公共收尾：分支只决定差异，公共结果只组装一次。 */
public final class CommonTailDemo {

    private static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("1000.00");

    private CommonTailDemo() {
    }

    public static void main(String[] args) {
        Order order = new Order("O1001", new BigDecimal("1280.00"));

        System.out.println("直接写法结果：" + reviewDirectly(order));
        System.out.println("改进写法结果：" + reviewWithCommonTail(order));
    }

    private static String reviewDirectly(Order order) {
        if (order.amount().compareTo(REVIEW_THRESHOLD) >= 0) {
            return "人工审核 | 订单=" + order.id() + " | 审核记录已保存";
        }
        return "自动通过 | 订单=" + order.id() + " | 审核记录已保存";
    }

    private static String reviewWithCommonTail(Order order) {
        String conclusion = order.amount().compareTo(REVIEW_THRESHOLD) >= 0
                ? "人工审核"
                : "自动通过";
        return conclusion + finishReview(order);
    }

    private static String finishReview(Order order) {
        return " | 订单=" + order.id() + " | 审核记录已保存";
    }

    private record Order(String id, BigDecimal amount) {
    }
}
