package com.xt.xiaoxingxing.playground.features.codedesign.controlflow;

import java.util.List;

/** 分步流程：用命名方法呈现校验、转换、计算和组装。 */
public final class StepwiseFlowDemo {

    private StepwiseFlowDemo() {
    }

    public static void main(String[] args) {
        CreateOrderRequest request = new CreateOrderRequest(" c001 ", List.of(3_900, 6_100));

        System.out.println("直接写法结果：" + createDirectly(request));
        System.out.println("改进写法结果：" + createStepByStep(request));
    }

    private static OrderSummary createDirectly(CreateOrderRequest request) {
        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new IllegalArgumentException("客户编号不能为空");
        }
        if (request.itemPricesInCents() == null || request.itemPricesInCents().isEmpty()) {
            throw new IllegalArgumentException("订单至少包含一件商品");
        }
        for (int price : request.itemPricesInCents()) {
            if (price <= 0) {
                throw new IllegalArgumentException("商品价格必须大于 0");
            }
        }
        String customerId = request.customerId().trim().toUpperCase();
        int total = 0;
        for (int price : request.itemPricesInCents()) {
            total += price;
        }
        return new OrderSummary(customerId, request.itemPricesInCents().size(), total);
    }

    private static OrderSummary createStepByStep(CreateOrderRequest request) {
        validate(request);
        NormalizedOrder normalizedOrder = normalize(request);
        int total = calculateTotal(normalizedOrder);
        return assemble(normalizedOrder, total);
    }

    private static void validate(CreateOrderRequest request) {
        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new IllegalArgumentException("客户编号不能为空");
        }
        if (request.itemPricesInCents() == null || request.itemPricesInCents().isEmpty()) {
            throw new IllegalArgumentException("订单至少包含一件商品");
        }
        if (request.itemPricesInCents().stream().anyMatch(price -> price <= 0)) {
            throw new IllegalArgumentException("商品价格必须大于 0");
        }
    }

    private static NormalizedOrder normalize(CreateOrderRequest request) {
        return new NormalizedOrder(
                request.customerId().trim().toUpperCase(),
                List.copyOf(request.itemPricesInCents()));
    }

    private static int calculateTotal(NormalizedOrder order) {
        return order.itemPricesInCents().stream().mapToInt(Integer::intValue).sum();
    }

    private static OrderSummary assemble(NormalizedOrder order, int total) {
        return new OrderSummary(order.customerId(), order.itemPricesInCents().size(), total);
    }

    private record CreateOrderRequest(String customerId, List<Integer> itemPricesInCents) {
    }

    private record NormalizedOrder(String customerId, List<Integer> itemPricesInCents) {
    }

    private record OrderSummary(String customerId, int itemCount, int totalInCents) {
    }
}
