package com.xt.xiaoxingxing.playground.features.codedesign.controlflow;

import java.util.List;

/** Guard Clause：先处理失败条件，让主流程保持平直。 */
public final class GuardClauseDemo {

    private GuardClauseDemo() {
    }

    public static void main(String[] args) {
        CreateOrderRequest request = new CreateOrderRequest(
                "C001", List.of("Java 实战"), "上海市浦东新区");

        System.out.println("直接写法结果：" + createDirectly(request));
        System.out.println("改进写法结果：" + createWithGuards(request));

        CreateOrderRequest invalidRequest = new CreateOrderRequest("C001", List.of(), "上海市浦东新区");
        System.out.println("提前失败结果：" + createWithGuards(invalidRequest));
    }

    private static String createDirectly(CreateOrderRequest request) {
        if (request.customerId() != null && !request.customerId().isBlank()) {
            if (request.items() != null && !request.items().isEmpty()) {
                if (request.address() != null && !request.address().isBlank()) {
                    return "订单创建成功，商品数=" + request.items().size();
                } else {
                    return "收货地址不能为空";
                }
            } else {
                return "订单至少包含一件商品";
            }
        } else {
            return "客户编号不能为空";
        }
    }

    private static String createWithGuards(CreateOrderRequest request) {
        if (request.customerId() == null || request.customerId().isBlank()) {
            return "客户编号不能为空";
        }
        if (request.items() == null || request.items().isEmpty()) {
            return "订单至少包含一件商品";
        }
        if (request.address() == null || request.address().isBlank()) {
            return "收货地址不能为空";
        }
        return "订单创建成功，商品数=" + request.items().size();
    }

    private record CreateOrderRequest(String customerId, List<String> items, String address) {
    }
}
