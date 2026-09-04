package com.xt.xiaoxingxing.playground.features.codedesign.structure;

import java.util.HashSet;
import java.util.Set;

public final class ExplicitDependencyDemo {

    private ExplicitDependencyDemo() {
    }

    public static void main(String[] args) {
        String directResult = new DirectOrderService().place("ORDER-1001");

        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        OrderService orderService = new OrderService(repository);
        String improvedResult = orderService.place("ORDER-1001");

        System.out.println("直接写法：" + directResult);
        System.out.println("改进写法：" + improvedResult);
        System.out.println("外部可以观察或替换依赖：" + repository.exists("ORDER-1001"));
    }

    private static final class DirectOrderService {

        private String place(String orderId) {
            // 方法内部固定创建实现，调用方无法替换或观察这个依赖。
            OrderRepository repository = new InMemoryOrderRepository();
            repository.save(orderId);
            return orderId + " 已保存";
        }
    }

    private static final class OrderService {

        private final OrderRepository repository;

        private OrderService(OrderRepository repository) {
            this.repository = repository;
        }

        private String place(String orderId) {
            repository.save(orderId);
            return orderId + " 已保存";
        }
    }

    private interface OrderRepository {

        void save(String orderId);
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final Set<String> orderIds = new HashSet<>();

        @Override
        public void save(String orderId) {
            orderIds.add(orderId);
        }

        private boolean exists(String orderId) {
            return orderIds.contains(orderId);
        }
    }
}
