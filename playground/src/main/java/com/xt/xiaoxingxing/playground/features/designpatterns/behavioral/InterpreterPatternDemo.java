package com.xt.xiaoxingxing.playground.features.designpatterns.behavioral;

import java.math.BigDecimal;

/** 解释器模式：用小型表达式树解释简单且稳定的优惠资格规则。 */
public final class InterpreterPatternDemo {

    private static final BigDecimal THRESHOLD = new BigDecimal("100.00");

    private InterpreterPatternDemo() {
    }

    public static void main(String[] args) {
        Order order = new Order(true, new BigDecimal("168.00"));

        boolean directResult = order.vip() && order.amount().compareTo(THRESHOLD) >= 0;
        EligibilityExpression expression = new AndExpression(
                new VipExpression(), new AmountAtLeastExpression(THRESHOLD));
        boolean patternResult = expression.interpret(order);

        System.out.println("直接写法：" + directResult);
        System.out.println("解释器模式：" + patternResult);
        System.out.println("业务结果一致：" + (directResult == patternResult));

        Order ordinaryOrder = new Order(false, new BigDecimal("168.00"));
        System.out.println("普通会员不满足规则：" + expression.interpret(ordinaryOrder));
        System.out.println("复杂规则应使用成熟解析器或规则引擎，不要无限扩展表达式类。");
    }

    private interface EligibilityExpression {

        boolean interpret(Order order);
    }

    private static final class VipExpression implements EligibilityExpression {

        @Override
        public boolean interpret(Order order) {
            return order.vip();
        }
    }

    private record AmountAtLeastExpression(BigDecimal minimum) implements EligibilityExpression {

        @Override
        public boolean interpret(Order order) {
            return order.amount().compareTo(minimum) >= 0;
        }
    }

    private record AndExpression(
            EligibilityExpression left,
            EligibilityExpression right
    ) implements EligibilityExpression {

        @Override
        public boolean interpret(Order order) {
            return left.interpret(order) && right.interpret(order);
        }
    }

    private record Order(boolean vip, BigDecimal amount) {
    }
}
