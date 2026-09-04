package com.xt.xiaoxingxing.playground.features.codedesign.controlflow;

/** 可读条件：把复杂布尔表达式翻译为有业务含义的方法。 */
public final class ReadableConditionDemo {

    private ReadableConditionDemo() {
    }

    public static void main(String[] args) {
        RefundRequest request = new RefundRequest(OrderState.SHIPPED, 12_800, false, 3);

        System.out.println("直接写法结果：" + canRefundDirectly(request));
        System.out.println("改进写法结果：" + canRefundReadably(request));
    }

    private static boolean canRefundDirectly(RefundRequest request) {
        return (request.status() == OrderState.PAID || request.status() == OrderState.SHIPPED)
                && request.paidAmountInCents() > 0
                && !request.riskOrder()
                && request.daysSincePayment() >= 0
                && request.daysSincePayment() <= 7;
    }

    private static boolean canRefundReadably(RefundRequest request) {
        return hasRefundableStatus(request)
                && hasPayment(request)
                && isSafeOrder(request)
                && isWithinRefundWindow(request);
    }

    private static boolean hasRefundableStatus(RefundRequest request) {
        return request.status() == OrderState.PAID || request.status() == OrderState.SHIPPED;
    }

    private static boolean hasPayment(RefundRequest request) {
        return request.paidAmountInCents() > 0;
    }

    private static boolean isSafeOrder(RefundRequest request) {
        return !request.riskOrder();
    }

    private static boolean isWithinRefundWindow(RefundRequest request) {
        return request.daysSincePayment() >= 0 && request.daysSincePayment() <= 7;
    }

    private record RefundRequest(
            OrderState status, int paidAmountInCents, boolean riskOrder, int daysSincePayment) {
    }

    private enum OrderState {
        CREATED, PAID, SHIPPED, CANCELLED
    }
}
