package com.xt.xiaoxingxing.playground.features.codedesign.modeling;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class AggregateConsistencyDemo {

    private AggregateConsistencyDemo() {
    }

    public static void main(String[] args) {
        List<OrderItem> directItems = new ArrayList<>();
        directItems.add(new OrderItem("Java 书籍", new BigDecimal("20.00"), 2));
        directItems.add(new OrderItem("笔记本", new BigDecimal("10.00"), 1));
        BigDecimal directTotal = new BigDecimal("50.00");

        Order improvedOrder = new Order();
        improvedOrder.addItem(new OrderItem("Java 书籍", new BigDecimal("20.00"), 2));
        improvedOrder.addItem(new OrderItem("笔记本", new BigDecimal("10.00"), 1));

        System.out.println("直接写法总额：" + directTotal);
        System.out.println("改进写法总额：" + improvedOrder.total());
        System.out.println("业务结果一致：" + (directTotal.compareTo(improvedOrder.total()) == 0));

        directItems.add(new OrderItem("鼠标垫", new BigDecimal("10.00"), 1));
        System.out.println("直接写法新增明细后：记录总额=" + directTotal
                + "，真实总额=" + calculateTotal(directItems));

        improvedOrder.addItem(new OrderItem("鼠标垫", new BigDecimal("10.00"), 1));
        System.out.println("订单统一维护后的总额：" + improvedOrder.total());
    }

    private static BigDecimal calculateTotal(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private record OrderItem(String productName, BigDecimal unitPrice, int quantity) {

        private OrderItem {
            if (quantity <= 0 || unitPrice.signum() < 0) {
                throw new IllegalArgumentException("数量必须大于零且单价不能为负数");
            }
        }

        private BigDecimal subtotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    private static final class Order {

        private final List<OrderItem> items = new ArrayList<>();

        private void addItem(OrderItem item) {
            items.add(item);
        }

        private BigDecimal total() {
            return calculateTotal(items);
        }
    }
}
