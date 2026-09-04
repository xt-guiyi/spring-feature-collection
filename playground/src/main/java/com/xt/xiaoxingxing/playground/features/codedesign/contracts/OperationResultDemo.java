package com.xt.xiaoxingxing.playground.features.codedesign.contracts;

/** 用明确的结果类型表达成功或失败，避免调用方猜测 null 的含义。 */
public final class OperationResultDemo {

    private OperationResultDemo() {
    }

    public static void main(String[] args) {
        Order direct = createDirect("O-1001", 100);
        CreateResult improved = createImproved("O-1001", 100);

        System.out.println("直接写法：" + direct);
        if (improved instanceof Success success) {
            System.out.println("改进写法：" + success.order());
        }

        System.out.println("直接失败只得到：" + createDirect("O-1002", 0));
        if (createImproved("O-1002", 0) instanceof Failure failure) {
            System.out.println("明确失败信息：" + failure.code() + "，" + failure.message());
        }
    }

    private static Order createDirect(String orderId, int amount) {
        return amount > 0 ? new Order(orderId, amount) : null;
    }

    private static CreateResult createImproved(String orderId, int amount) {
        if (amount <= 0) {
            return new Failure("INVALID_AMOUNT", "订单金额必须大于 0");
        }
        return new Success(new Order(orderId, amount));
    }

    private sealed interface CreateResult permits Success, Failure {
    }

    private record Success(Order order) implements CreateResult {
    }

    private record Failure(String code, String message) implements CreateResult {
    }

    private record Order(String id, int amount) {
    }
}
