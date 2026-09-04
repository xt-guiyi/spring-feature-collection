package com.xt.xiaoxingxing.playground.features.codedesign.structure;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public final class ControllableTimeDemo {

    private ControllableTimeDemo() {
    }

    public static void main(String[] args) {
        Order directOrder = directCreate("ORDER-1001");

        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-09-04T02:00:00Z"),
                ZoneOffset.UTC
        );
        OrderService orderService = new OrderService(fixedClock);
        Order improvedOrder = orderService.create("ORDER-1001");

        System.out.println("直接写法的时间每次不同：" + directOrder.createdAt());
        System.out.println("改进写法的时间可控制：" + improvedOrder.createdAt());
    }

    private static Order directCreate(String orderId) {
        // 业务方法直接读取系统时间，结果无法由调用方控制。
        return new Order(orderId, Instant.now());
    }

    private static final class OrderService {

        private final Clock clock;

        private OrderService(Clock clock) {
            this.clock = clock;
        }

        private Order create(String orderId) {
            return new Order(orderId, clock.instant());
        }
    }

    private record Order(String id, Instant createdAt) {
    }
}
