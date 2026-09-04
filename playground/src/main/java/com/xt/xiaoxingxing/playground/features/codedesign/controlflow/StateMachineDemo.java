package com.xt.xiaoxingxing.playground.features.codedesign.controlflow;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 状态机：集中表达订单允许发生的状态迁移。 */
public final class StateMachineDemo {

    private StateMachineDemo() {
    }

    public static void main(String[] args) {
        List<OrderEvent> events = List.of(OrderEvent.PAY, OrderEvent.SHIP, OrderEvent.FINISH);

        OrderState directState = OrderState.CREATED;
        OrderState improvedState = OrderState.CREATED;
        for (OrderEvent event : events) {
            directState = directNext(directState, event);
            improvedState = TransitionTable.next(improvedState, event);
        }

        System.out.println("直接写法结果：" + directState);
        System.out.println("改进写法结果：" + improvedState);

        try {
            TransitionTable.next(OrderState.PAID, OrderEvent.FINISH);
        } catch (IllegalStateException exception) {
            System.out.println("非法迁移：" + exception.getMessage());
        }
    }

    private static OrderState directNext(OrderState current, OrderEvent event) {
        if (current == OrderState.CREATED && event == OrderEvent.PAY) {
            return OrderState.PAID;
        }
        if (current == OrderState.CREATED && event == OrderEvent.CANCEL) {
            return OrderState.CANCELLED;
        }
        if (current == OrderState.PAID && event == OrderEvent.SHIP) {
            return OrderState.SHIPPED;
        }
        if (current == OrderState.PAID && event == OrderEvent.CANCEL) {
            return OrderState.CANCELLED;
        }
        if (current == OrderState.SHIPPED && event == OrderEvent.FINISH) {
            return OrderState.COMPLETED;
        }
        throw new IllegalStateException("不允许 " + current + " + " + event);
    }

    private static final class TransitionTable {

        private static final Map<OrderState, Map<OrderEvent, OrderState>> TRANSITIONS = createTransitions();

        private static OrderState next(OrderState current, OrderEvent event) {
            OrderState next = TRANSITIONS.getOrDefault(current, Map.of()).get(event);
            if (next == null) {
                throw new IllegalStateException("不允许 " + current + " + " + event);
            }
            return next;
        }

        private static Map<OrderState, Map<OrderEvent, OrderState>> createTransitions() {
            Map<OrderState, Map<OrderEvent, OrderState>> transitions = new EnumMap<>(OrderState.class);
            transitions.put(OrderState.CREATED, Map.of(
                    OrderEvent.PAY, OrderState.PAID,
                    OrderEvent.CANCEL, OrderState.CANCELLED));
            transitions.put(OrderState.PAID, Map.of(
                    OrderEvent.SHIP, OrderState.SHIPPED,
                    OrderEvent.CANCEL, OrderState.CANCELLED));
            transitions.put(OrderState.SHIPPED, Map.of(OrderEvent.FINISH, OrderState.COMPLETED));
            return transitions;
        }
    }

    private enum OrderState {
        CREATED, PAID, SHIPPED, COMPLETED, CANCELLED
    }

    private enum OrderEvent {
        PAY, SHIP, FINISH, CANCEL
    }
}
