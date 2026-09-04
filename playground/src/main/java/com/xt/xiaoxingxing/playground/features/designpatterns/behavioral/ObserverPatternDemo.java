package com.xt.xiaoxingxing.playground.features.designpatterns.behavioral;

import java.util.ArrayList;
import java.util.List;

/** 观察者模式：订单支付后向多个可独立扩展的监听器发送同步通知。 */
public final class ObserverPatternDemo {

    private ObserverPatternDemo() {
    }

    public static void main(String[] args) {
        List<String> directRecords = new ArrayList<>();
        payDirectly("ORDER-1001", directRecords);

        List<String> patternRecords = new ArrayList<>();
        Order order = new Order("ORDER-1001");
        order.subscribe(new PointsListener(patternRecords));
        order.subscribe(new MessageListener(patternRecords));
        order.subscribe(new AuditListener(patternRecords));
        order.pay();

        System.out.println("直接写法：" + directRecords);
        System.out.println("观察者模式：" + patternRecords);
        System.out.println("业务结果一致：" + directRecords.equals(patternRecords));
    }

    private static void payDirectly(String orderId, List<String> records) {
        // 每增加一个后续动作，都要继续修改支付方法。
        records.add("增加积分：" + orderId);
        records.add("发送消息：" + orderId);
        records.add("记录审计：" + orderId);
    }

    private interface OrderPaidListener {

        void onPaid(String orderId);
    }

    private static final class Order {

        private final String id;
        private final List<OrderPaidListener> listeners = new ArrayList<>();

        private Order(String id) {
            this.id = id;
        }

        private void subscribe(OrderPaidListener listener) {
            listeners.add(listener);
        }

        private void pay() {
            // 这里只演示单 JVM 内的同步通知，不代表 MQ 可靠投递。
            listeners.forEach(listener -> listener.onPaid(id));
        }
    }

    private record PointsListener(List<String> records) implements OrderPaidListener {

        @Override
        public void onPaid(String orderId) {
            records.add("增加积分：" + orderId);
        }
    }

    private record MessageListener(List<String> records) implements OrderPaidListener {

        @Override
        public void onPaid(String orderId) {
            records.add("发送消息：" + orderId);
        }
    }

    private record AuditListener(List<String> records) implements OrderPaidListener {

        @Override
        public void onPaid(String orderId) {
            records.add("记录审计：" + orderId);
        }
    }
}
