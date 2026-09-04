package com.xt.xiaoxingxing.playground.features.codedesign.contracts;

import java.util.Locale;

/** 在系统边界统一清洗输入，内部代码只处理合法数据。 */
public final class BoundaryNormalizationDemo {

    private BoundaryNormalizationDemo() {
    }

    public static void main(String[] args) {
        OrderSummary direct = createDirect(" O-1001 ", " vip ", 100);
        OrderCommand command = OrderCommand.from(" O-1001 ", " vip ", 100);
        OrderSummary improved = createImproved(command);

        System.out.println("直接写法：" + direct);
        System.out.println("改进写法：" + improved);

        System.out.println("直接写法接受了空渠道：" + createDirect("O-1002", " ", 100));
        try {
            OrderCommand.from("O-1002", " ", 100);
        } catch (IllegalArgumentException exception) {
            System.out.println("边界校验拒绝空渠道：" + exception.getMessage());
        }
    }

    private static OrderSummary createDirect(String orderId, String channel, int amount) {
        // 每个使用点都必须记得 trim 和大小写转换。
        String displayChannel = channel.trim().toUpperCase(Locale.ROOT);
        int payable = "VIP".equals(channel.trim().toUpperCase(Locale.ROOT)) ? amount - 10 : amount;
        return new OrderSummary(orderId.trim(), displayChannel, payable);
    }

    private static OrderSummary createImproved(OrderCommand command) {
        int payable = command.channel() == Channel.VIP ? command.amount() - 10 : command.amount();
        return new OrderSummary(command.orderId(), command.channel().name(), payable);
    }

    private record OrderCommand(String orderId, Channel channel, int amount) {

        private static OrderCommand from(String orderId, String channel, int amount) {
            String normalizedId = orderId == null ? "" : orderId.trim();
            String normalizedChannel = channel == null ? "" : channel.trim().toUpperCase(Locale.ROOT);
            if (normalizedId.isEmpty() || normalizedChannel.isEmpty()) {
                throw new IllegalArgumentException("订单号和渠道不能为空");
            }
            return new OrderCommand(normalizedId, Channel.valueOf(normalizedChannel), amount);
        }
    }

    private enum Channel {
        NORMAL, VIP
    }

    private record OrderSummary(String orderId, String channel, int payable) {
    }
}
