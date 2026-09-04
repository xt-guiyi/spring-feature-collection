package com.xt.xiaoxingxing.playground.features.codedesign.modeling;

import java.math.BigDecimal;
import java.util.Objects;

public final class ParameterObjectDemo {

    private ParameterObjectDemo() {
    }

    public static void main(String[] args) {
        OrderSummary directResult = directCreate(
                "ORDER-1001", "USER-01", "Java 书籍", 2,
                new BigDecimal("49.90"), "上海市", "工作日送达");

        CreateOrderCommand command = new CreateOrderCommand(
                "ORDER-1001", "USER-01", "Java 书籍", 2,
                new BigDecimal("49.90"), "上海市", "工作日送达");
        OrderSummary improvedResult = improvedCreate(command);

        System.out.println("直接写法：" + directResult);
        System.out.println("改进写法：" + improvedResult);
        System.out.println("业务结果一致：" + directResult.equals(improvedResult));
    }

    // 零散参数会让业务方法签名不断膨胀，也不方便把一次操作整体传递。
    private static OrderSummary directCreate(String orderId, String customerId,
                                             String productName, int quantity,
                                             BigDecimal unitPrice, String address,
                                             String remark) {
        return new OrderSummary(orderId, customerId, productName,
                unitPrice.multiply(BigDecimal.valueOf(quantity)), address, remark);
    }

    private static OrderSummary improvedCreate(CreateOrderCommand command) {
        return new OrderSummary(command.orderId(), command.customerId(), command.productName(),
                command.unitPrice().multiply(BigDecimal.valueOf(command.quantity())),
                command.address(), command.remark());
    }

    private record CreateOrderCommand(String orderId, String customerId, String productName,
                                      int quantity, BigDecimal unitPrice,
                                      String address, String remark) {

        private CreateOrderCommand {
            Objects.requireNonNull(unitPrice, "单价不能为空");
            if (orderId == null || orderId.isBlank() || quantity <= 0) {
                throw new IllegalArgumentException("订单编号不能为空且数量必须大于零");
            }
        }
    }

    private record OrderSummary(String orderId, String customerId, String productName,
                                BigDecimal total, String address, String remark) {
    }
}
