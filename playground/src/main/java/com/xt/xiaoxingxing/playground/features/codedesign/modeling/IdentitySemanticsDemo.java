package com.xt.xiaoxingxing.playground.features.codedesign.modeling;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class IdentitySemanticsDemo {

    private IdentitySemanticsDemo() {
    }

    public static void main(String[] args) {
        DirectOrder directBefore = new DirectOrder("ORDER-1001", "上海市");
        DirectOrder directAfter = new DirectOrder("ORDER-1001", "北京市");
        boolean directSameOrder = directBefore.orderId().equals(directAfter.orderId());

        Order improvedBefore = new Order(new OrderId("ORDER-1001"), "上海市");
        Order improvedAfter = new Order(new OrderId("ORDER-1001"), "北京市");
        boolean improvedSameOrder = improvedBefore.equals(improvedAfter);

        System.out.println("直接写法手动比较编号：" + directSameOrder);
        System.out.println("改进写法由对象表达身份：" + improvedSameOrder);
        System.out.println("业务结果一致：" + (directSameOrder == improvedSameOrder));

        Set<DirectOrder> directOrders = new HashSet<>();
        directOrders.add(directBefore);
        directOrders.add(directAfter);
        Set<Order> improvedOrders = new HashSet<>();
        improvedOrders.add(improvedBefore);
        improvedOrders.add(improvedAfter);

        System.out.println("所有字段参与相等判断，集合中出现两个同一订单：" + directOrders.size());
        System.out.println("只按订单身份判断，集合中订单数量：" + improvedOrders.size());
    }

    private record DirectOrder(String orderId, String address) {
    }

    private record OrderId(String value) {
    }

    private static final class Order {

        private final OrderId id;
        private final String address;

        private Order(OrderId id, String address) {
            this.id = id;
            this.address = address;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof Order other && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
}
