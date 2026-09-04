package com.xt.xiaoxingxing.playground.features.codedesign.contracts;

/** 用有业务含义的方法名代替难以理解的布尔参数。 */
public final class FlagArgumentDemo {

    private FlagArgumentDemo() {
    }

    public static void main(String[] args) {
        Order order = new Order("O-1001", Status.CREATED);

        Order direct = changeStatusDirect(order, true);
        Order improved = cancel(order);

        System.out.println("直接写法：" + direct);
        System.out.println("改进写法：" + improved);
        System.out.println("直接写法中的 false 含义：" + changeStatusDirect(order, false).status());
        System.out.println("改进写法的方法名直接表达含义：" + pay(order).status());
    }

    private static Order changeStatusDirect(Order order, boolean cancel) {
        return new Order(order.id(), cancel ? Status.CANCELED : Status.PAID);
    }

    private static Order cancel(Order order) {
        return new Order(order.id(), Status.CANCELED);
    }

    private static Order pay(Order order) {
        return new Order(order.id(), Status.PAID);
    }

    private enum Status {
        CREATED, PAID, CANCELED
    }

    private record Order(String id, Status status) {
    }
}
