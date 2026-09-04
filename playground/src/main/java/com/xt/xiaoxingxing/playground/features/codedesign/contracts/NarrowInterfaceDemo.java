package com.xt.xiaoxingxing.playground.features.codedesign.contracts;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 查询代码只依赖读取能力，不获得不需要的保存和删除能力。 */
public final class NarrowInterfaceDemo {

    private NarrowInterfaceDemo() {
    }

    public static void main(String[] args) {
        InMemoryOrderRepository repository = new InMemoryOrderRepository();
        repository.save(new Order("O-1001", 100));

        DirectOrderQuery directQuery = new DirectOrderQuery(repository);
        OrderQuery improvedQuery = new OrderQuery(repository);

        System.out.println("直接写法：" + directQuery.findSummary("O-1001"));
        System.out.println("改进写法：" + improvedQuery.findSummary("O-1001"));
    }

    private interface OrderReader {

        Optional<Order> findById(String orderId);
    }

    private interface OrderRepository extends OrderReader {

        void save(Order order);

        void deleteById(String orderId);
    }

    private static final class DirectOrderQuery {

        private final OrderRepository repository;

        private DirectOrderQuery(OrderRepository repository) {
            this.repository = repository;
        }

        private String findSummary(String orderId) {
            return repository.findById(orderId).map(Order::summary).orElse("订单不存在");
        }
    }

    private static final class OrderQuery {

        private final OrderReader reader;

        private OrderQuery(OrderReader reader) {
            this.reader = reader;
        }

        private String findSummary(String orderId) {
            return reader.findById(orderId).map(Order::summary).orElse("订单不存在");
        }
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final Map<String, Order> orders = new HashMap<>();

        @Override
        public Optional<Order> findById(String orderId) {
            return Optional.ofNullable(orders.get(orderId));
        }

        @Override
        public void save(Order order) {
            orders.put(order.id(), order);
        }

        @Override
        public void deleteById(String orderId) {
            orders.remove(orderId);
        }
    }

    private record Order(String id, int amount) {

        private String summary() {
            return "%s，应付 %d 元".formatted(id, amount);
        }
    }
}
