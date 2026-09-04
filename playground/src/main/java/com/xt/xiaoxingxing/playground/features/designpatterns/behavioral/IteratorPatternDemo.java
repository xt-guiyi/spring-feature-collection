package com.xt.xiaoxingxing.playground.features.designpatterns.behavioral;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/** 迭代器模式：隐藏订单分页读取，让调用方只关注逐条遍历。 */
public final class IteratorPatternDemo {

    private IteratorPatternDemo() {
    }

    public static void main(String[] args) {
        List<Order> data = List.of(
                new Order("O-1001"), new Order("O-1002"),
                new Order("O-1003"), new Order("O-1004"), new Order("O-1005"));

        List<String> directResult = readPagesDirectly(new InMemoryOrderPageSource(data), 2);

        InMemoryOrderPageSource patternSource = new InMemoryOrderPageSource(data);
        List<String> patternResult = new ArrayList<>();
        for (Order order : new PagedOrders(patternSource, 2)) {
            patternResult.add(order.orderNo());
        }

        System.out.println("直接写法：" + directResult);
        System.out.println("迭代器模式：" + patternResult);
        System.out.println("业务结果一致：" + directResult.equals(patternResult));

        InMemoryOrderPageSource lazySource = new InMemoryOrderPageSource(data);
        int visited = 0;
        for (Order ignored : new PagedOrders(lazySource, 2)) {
            if (++visited == 3) {
                break;
            }
        }
        System.out.println("只遍历三条时实际读取页数：" + lazySource.fetchCount());
    }

    private static List<String> readPagesDirectly(OrderPageSource source, int pageSize) {
        List<String> orderNos = new ArrayList<>();
        for (int pageNumber = 0; ; pageNumber++) {
            List<Order> page = source.fetch(pageNumber, pageSize);
            if (page.isEmpty()) {
                return orderNos;
            }
            page.stream().map(Order::orderNo).forEach(orderNos::add);
        }
    }

    private interface OrderPageSource {

        List<Order> fetch(int pageNumber, int pageSize);
    }

    private static final class InMemoryOrderPageSource implements OrderPageSource {

        private final List<Order> orders;
        private int fetchCount;

        private InMemoryOrderPageSource(List<Order> orders) {
            this.orders = List.copyOf(orders);
        }

        @Override
        public List<Order> fetch(int pageNumber, int pageSize) {
            fetchCount++;
            int from = pageNumber * pageSize;
            if (from >= orders.size()) {
                return List.of();
            }
            return List.copyOf(orders.subList(from, Math.min(from + pageSize, orders.size())));
        }

        private int fetchCount() {
            return fetchCount;
        }
    }

    private record PagedOrders(OrderPageSource source, int pageSize) implements Iterable<Order> {

        @Override
        public Iterator<Order> iterator() {
            return new PagedOrderIterator(source, pageSize);
        }
    }

    private static final class PagedOrderIterator implements Iterator<Order> {

        private final OrderPageSource source;
        private final int pageSize;
        private List<Order> currentPage = List.of();
        private int pageNumber;
        private int index;
        private boolean finished;

        private PagedOrderIterator(OrderPageSource source, int pageSize) {
            this.source = source;
            this.pageSize = pageSize;
        }

        @Override
        public boolean hasNext() {
            while (index >= currentPage.size() && !finished) {
                currentPage = source.fetch(pageNumber++, pageSize);
                index = 0;
                finished = currentPage.isEmpty();
            }
            return !finished;
        }

        @Override
        public Order next() {
            if (!hasNext()) {
                throw new NoSuchElementException("没有更多订单");
            }
            return currentPage.get(index++);
        }
    }

    private record Order(String orderNo) {
    }
}
