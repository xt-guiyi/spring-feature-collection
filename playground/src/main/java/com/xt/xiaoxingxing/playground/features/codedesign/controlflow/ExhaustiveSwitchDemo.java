package com.xt.xiaoxingxing.playground.features.codedesign.controlflow;

/** 完整枚举处理：让新增状态触发编译期提醒。 */
public final class ExhaustiveSwitchDemo {

    private ExhaustiveSwitchDemo() {
    }

    public static void main(String[] args) {
        OrderState state = OrderState.PAID;

        System.out.println("直接写法结果：" + directAction(state));
        System.out.println("改进写法结果：" + improvedAction(state));
        System.out.println("default 隐藏的问题：" + directAction(OrderState.SHIPPED));
        System.out.println("穷举后的结果：" + improvedAction(OrderState.SHIPPED));
    }

    private static String directAction(OrderState state) {
        return switch (state) {
            case CREATED -> "等待支付";
            case PAID -> "等待发货";
            default -> "无需处理";
        };
    }

    private static String improvedAction(OrderState state) {
        // 不写 default；新增枚举值但未处理时，编译器会提示这里不完整。
        return switch (state) {
            case CREATED -> "等待支付";
            case PAID -> "等待发货";
            case SHIPPED -> "等待签收";
            case CANCELLED -> "已取消";
        };
    }

    private enum OrderState {
        CREATED, PAID, SHIPPED, CANCELLED
    }
}
