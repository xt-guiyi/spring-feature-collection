package com.xt.xiaoxingxing.playground.features.designpatterns.behavioral;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** 命令模式：把支付、发货操作封装成可排队并统一执行的对象。 */
public final class CommandPatternDemo {

    private CommandPatternDemo() {
    }

    public static void main(String[] args) {
        OrderService directService = new OrderService();
        directService.pay("O-1001");
        directService.ship("O-1001");
        List<String> directResult = directService.operations();

        OrderService patternService = new OrderService();
        CommandExecutor executor = new CommandExecutor();
        executor.submit(new PayOrderCommand(patternService, "O-1001"));
        executor.submit(new ShipOrderCommand(patternService, "O-1001"));
        System.out.println("命令入队后、执行前：" + patternService.operations());
        executor.executeAll();
        List<String> patternResult = patternService.operations();

        System.out.println("直接写法：" + directResult);
        System.out.println("命令模式：" + patternResult);
        System.out.println("业务结果一致：" + directResult.equals(patternResult));
        System.out.println("统一保存执行记录：" + executor.history());
    }

    private interface OrderCommand {

        void execute();

        String description();
    }

    private static final class PayOrderCommand implements OrderCommand {

        private final OrderService receiver;
        private final String orderNo;

        private PayOrderCommand(OrderService receiver, String orderNo) {
            this.receiver = receiver;
            this.orderNo = orderNo;
        }

        @Override
        public void execute() {
            receiver.pay(orderNo);
        }

        @Override
        public String description() {
            return "支付订单 " + orderNo;
        }
    }

    private static final class ShipOrderCommand implements OrderCommand {

        private final OrderService receiver;
        private final String orderNo;

        private ShipOrderCommand(OrderService receiver, String orderNo) {
            this.receiver = receiver;
            this.orderNo = orderNo;
        }

        @Override
        public void execute() {
            receiver.ship(orderNo);
        }

        @Override
        public String description() {
            return "发货订单 " + orderNo;
        }
    }

    private static final class CommandExecutor {

        private final Deque<OrderCommand> queue = new ArrayDeque<>();
        private final List<String> history = new ArrayList<>();

        private void submit(OrderCommand command) {
            queue.addLast(command);
        }

        private void executeAll() {
            while (!queue.isEmpty()) {
                OrderCommand command = queue.removeFirst();
                command.execute();
                history.add(command.description());
            }
        }

        private List<String> history() {
            return List.copyOf(history);
        }
    }

    private static final class OrderService {

        private final List<String> operations = new ArrayList<>();

        private void pay(String orderNo) {
            operations.add(orderNo + " 已支付");
        }

        private void ship(String orderNo) {
            operations.add(orderNo + " 已发货");
        }

        private List<String> operations() {
            return List.copyOf(operations);
        }
    }
}
