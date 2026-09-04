package com.xt.xiaoxingxing.playground.features.codedesign.structure;

import java.util.ArrayList;
import java.util.List;

public final class OwnedStateDemo {

    private OwnedStateDemo() {
    }

    public static void main(String[] args) {
        DirectOrderRegistry.clear();
        DirectOrderRegistry directStoreA = new DirectOrderRegistry();
        DirectOrderRegistry directStoreB = new DirectOrderRegistry();
        directStoreA.add("ORDER-1001");

        OrderRegistry improvedStoreA = new OrderRegistry();
        OrderRegistry improvedStoreB = new OrderRegistry();
        improvedStoreA.add("ORDER-1001");

        System.out.println("直接写法，B 意外看到 A 的数据：" + directStoreB.orders());
        System.out.println("改进写法，A 自己的数据：" + improvedStoreA.orders());
        System.out.println("改进写法，B 保持独立：" + improvedStoreB.orders());
    }

    private static final class DirectOrderRegistry {

        // 静态可变集合属于整个 JVM，而不是某个 registry 实例。
        private static final List<String> ORDERS = new ArrayList<>();

        private void add(String orderId) {
            ORDERS.add(orderId);
        }

        private List<String> orders() {
            return List.copyOf(ORDERS);
        }

        private static void clear() {
            ORDERS.clear();
        }
    }

    private static final class OrderRegistry {

        private final List<String> orders = new ArrayList<>();

        private void add(String orderId) {
            orders.add(orderId);
        }

        private List<String> orders() {
            return List.copyOf(orders);
        }
    }
}
