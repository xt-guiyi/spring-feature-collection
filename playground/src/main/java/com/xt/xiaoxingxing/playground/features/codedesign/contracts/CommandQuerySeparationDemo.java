package com.xt.xiaoxingxing.playground.features.codedesign.contracts;

/** 写操作负责改变状态，读操作负责返回视图。 */
public final class CommandQuerySeparationDemo {

    private CommandQuerySeparationDemo() {
    }

    public static void main(String[] args) {
        Order directOrder = new Order("O-1001", 100);
        Order improvedOrder = new Order("O-1001", 100);

        OrderView direct = payAndQueryDirect(directOrder);
        pay(improvedOrder);
        OrderView improved = query(improvedOrder);

        System.out.println("直接写法：" + direct);
        System.out.println("改进写法：" + improved);
    }

    private static OrderView payAndQueryDirect(Order order) {
        order.status = Status.PAID;
        return new OrderView(order.id, order.status, "已支付 %d 元".formatted(order.amount));
    }

    private static void pay(Order order) {
        order.status = Status.PAID;
    }

    private static OrderView query(Order order) {
        return new OrderView(order.id, order.status, "已支付 %d 元".formatted(order.amount));
    }

    private enum Status {
        CREATED, PAID
    }

    private static final class Order {

        private final String id;
        private final int amount;
        private Status status = Status.CREATED;

        private Order(String id, int amount) {
            this.id = id;
            this.amount = amount;
        }
    }

    private record OrderView(String id, Status status, String description) {
    }
}
