package com.xt.xiaoxingxing.playground.features.codedesign.contracts;

/** 输入、领域对象和输出模型各自只表达自己的职责。 */
public final class ModelBoundaryDemo {

    private ModelBoundaryDemo() {
    }

    public static void main(String[] args) {
        LegacyOrder request = new LegacyOrder(null, "小星星", 100, 0);
        LegacyOrder direct = createDirect(request);

        CreateOrderRequest improvedRequest = new CreateOrderRequest("小星星", 100);
        Order order = createImproved(improvedRequest);
        OrderResponse improved = toResponse(order);

        System.out.println("直接写法：" + direct.displayText());
        System.out.println("改进写法：" + improved.displayText());
        System.out.println("共用模型额外暴露内部成本：" + direct.internalCost);
    }

    private static LegacyOrder createDirect(LegacyOrder model) {
        // 请求对象同时被当作领域对象和返回对象，还会被保存逻辑修改。
        model.id = "O-1001";
        model.internalCost = 70;
        return model;
    }

    private static Order createImproved(CreateOrderRequest request) {
        return new Order("O-1001", request.customerName(), request.amount(), 70);
    }

    private static OrderResponse toResponse(Order order) {
        return new OrderResponse(order.id(), order.customerName(), order.amount());
    }

    private static final class LegacyOrder {

        private String id;
        private final String customerName;
        private final int amount;
        private int internalCost;

        private LegacyOrder(String id, String customerName, int amount, int internalCost) {
            this.id = id;
            this.customerName = customerName;
            this.amount = amount;
            this.internalCost = internalCost;
        }

        private String displayText() {
            return "%s：%s，应付 %d 元".formatted(id, customerName, amount);
        }
    }

    private record CreateOrderRequest(String customerName, int amount) {
    }

    private record Order(String id, String customerName, int amount, int internalCost) {
    }

    private record OrderResponse(String id, String customerName, int amount) {

        private String displayText() {
            return "%s：%s，应付 %d 元".formatted(id, customerName, amount);
        }
    }
}
