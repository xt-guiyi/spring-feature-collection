package com.xt.xiaoxingxing.playground.features.designpatterns.behavioral;

import java.util.ArrayList;
import java.util.List;

/** 中介者模式：取消订单相关组件只通知中介者，不再互相持有并调用。 */
public final class MediatorPatternDemo {

    private MediatorPatternDemo() {
    }

    public static void main(String[] args) {
        List<String> directActions = runDirectly("O-1001");

        List<String> patternActions = new ArrayList<>();
        CancellationCoordinator coordinator = new CancellationCoordinator(patternActions);
        coordinator.order().cancel("O-1001");

        System.out.println("直接写法：" + directActions);
        System.out.println("中介者模式：" + patternActions);
        System.out.println("业务结果一致：" + directActions.equals(patternActions));
        System.out.println("增加协作组件只修改中介者；中介者过大时也要继续拆分业务。");
    }

    private static List<String> runDirectly(String orderNo) {
        List<String> actions = new ArrayList<>();
        DirectOrder order = new DirectOrder(actions);
        DirectCoupon coupon = new DirectCoupon(actions, order);
        DirectInventory inventory = new DirectInventory(actions, coupon);
        DirectPayment payment = new DirectPayment(actions, inventory);
        order.connect(payment);
        order.cancel(orderNo);
        return List.copyOf(actions);
    }

    private static final class DirectOrder {

        private final List<String> actions;
        private DirectPayment payment;

        private DirectOrder(List<String> actions) {
            this.actions = actions;
        }

        private void connect(DirectPayment payment) {
            this.payment = payment;
        }

        private void cancel(String orderNo) {
            actions.add(orderNo + " 已取消");
            payment.refund(orderNo);
        }

        private void completeCancellation(String orderNo) {
            actions.add(orderNo + " 取消处理完成");
        }
    }

    private record DirectPayment(List<String> actions, DirectInventory inventory) {

        private void refund(String orderNo) {
            actions.add(orderNo + " 支付已退款");
            inventory.release(orderNo);
        }
    }

    private record DirectInventory(
            List<String> actions,
            DirectCoupon coupon
    ) {

        private void release(String orderNo) {
            actions.add(orderNo + " 库存已释放");
            coupon.returnToCustomer(orderNo);
        }
    }

    private record DirectCoupon(List<String> actions, DirectOrder order) {

        private void returnToCustomer(String orderNo) {
            actions.add(orderNo + " 优惠券已返还");
            order.completeCancellation(orderNo);
        }
    }

    private interface CancellationMediator {

        void notify(CancellationEvent event, String orderNo);
    }

    private record OrderComponent(CancellationMediator mediator, List<String> actions) {
        private void cancel(String orderNo) {
            actions.add(orderNo + " 已取消");
            mediator.notify(CancellationEvent.ORDER_CANCELLED, orderNo);
        }

        private void completeCancellation(String orderNo) {
            actions.add(orderNo + " 取消处理完成");
        }
    }

    private record PaymentComponent(CancellationMediator mediator, List<String> actions) {
        private void refund(String orderNo) {
            actions.add(orderNo + " 支付已退款");
            mediator.notify(CancellationEvent.PAYMENT_REFUNDED, orderNo);
        }
    }

    private record InventoryComponent(CancellationMediator mediator, List<String> actions) {
        private void release(String orderNo) {
            actions.add(orderNo + " 库存已释放");
            mediator.notify(CancellationEvent.STOCK_RELEASED, orderNo);
        }
    }

    private record CouponComponent(CancellationMediator mediator, List<String> actions) {
        private void returnToCustomer(String orderNo) {
            actions.add(orderNo + " 优惠券已返还");
            mediator.notify(CancellationEvent.COUPON_RETURNED, orderNo);
        }
    }

    private static final class CancellationCoordinator implements CancellationMediator {

        private final OrderComponent order;
        private final PaymentComponent payment;
        private final InventoryComponent inventory;
        private final CouponComponent coupon;

        private CancellationCoordinator(List<String> actions) {
            order = new OrderComponent(this, actions);
            payment = new PaymentComponent(this, actions);
            inventory = new InventoryComponent(this, actions);
            coupon = new CouponComponent(this, actions);
        }

        @Override
        public void notify(CancellationEvent event, String orderNo) {
            switch (event) {
                case ORDER_CANCELLED -> payment.refund(orderNo);
                case PAYMENT_REFUNDED -> inventory.release(orderNo);
                case STOCK_RELEASED -> coupon.returnToCustomer(orderNo);
                case COUPON_RETURNED -> order.completeCancellation(orderNo);
            }
        }

        private OrderComponent order() {
            return order;
        }
    }

    private enum CancellationEvent {
        ORDER_CANCELLED, PAYMENT_REFUNDED, STOCK_RELEASED, COUPON_RETURNED
    }
}
