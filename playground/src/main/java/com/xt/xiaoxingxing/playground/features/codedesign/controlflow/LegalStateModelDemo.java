package com.xt.xiaoxingxing.playground.features.codedesign.controlflow;

/** 合法状态模型：用一个状态替代可能互相矛盾的布尔字段。 */
public final class LegalStateModelDemo {

    private LegalStateModelDemo() {
    }

    public static void main(String[] args) {
        FlagOrder directOrder = new FlagOrder(true, false);
        Order improvedOrder = new Order(OrderState.PAID);

        System.out.println("直接写法结果：" + directOrder.status());
        System.out.println("改进写法结果：" + improvedOrder.status());

        FlagOrder illegalOrder = new FlagOrder(true, true);
        System.out.println("布尔字段风险：" + illegalOrder.status());
    }

    private record FlagOrder(boolean paid, boolean cancelled) {

        private String status() {
            if (paid && cancelled) {
                return "状态冲突：订单同时为已支付和已取消";
            }
            if (paid) {
                return OrderState.PAID.name();
            }
            if (cancelled) {
                return OrderState.CANCELLED.name();
            }
            return OrderState.CREATED.name();
        }
    }

    // 单个枚举值从类型上排除了“既支付又取消”的组合。
    private record Order(OrderState status) {
    }

    private enum OrderState {
        CREATED, PAID, CANCELLED
    }
}
