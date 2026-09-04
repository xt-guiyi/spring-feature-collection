package com.xt.xiaoxingxing.playground.features.designpatterns.structural;

import java.math.BigDecimal;

/** 外观：用一个结算入口协调库存、支付和订单子系统。 */
public final class FacadePatternDemo {

    private FacadePatternDemo() {
    }

    public static void main(String[] args) {
        CheckoutCommand command = new CheckoutCommand(
                "ORDER-1001", "SKU-BOOK", 2, new BigDecimal("198.00")
        );

        InventoryService directInventory = new InventoryService(10);
        PaymentService directPayment = new PaymentService();
        OrderService directOrder = new OrderService();
        Reservation reservation = directInventory.reserve(command.sku(), command.quantity());
        PaymentReceipt receipt = directPayment.charge(command.orderId(), command.amount());
        String directResult = directOrder.create(command.orderId(), reservation, receipt);

        CheckoutFacade facade = new CheckoutFacade(
                new InventoryService(10), new PaymentService(), new OrderService()
        );
        String patternResult = facade.checkout(command);

        System.out.println("直接调用子系统：" + directResult);
        System.out.println("外观统一入口：" + patternResult);

        PaymentService protectedPayment = new PaymentService();
        CheckoutFacade protectedFacade = new CheckoutFacade(
                new InventoryService(1), protectedPayment, new OrderService()
        );
        try {
            protectedFacade.checkout(new CheckoutCommand(
                    "ORDER-1002", "SKU-BOOK", 2, new BigDecimal("198.00")
            ));
        } catch (IllegalStateException exception) {
            System.out.println("库存不足时：" + exception.getMessage());
        }
        System.out.println("失败后支付调用次数：" + protectedPayment.chargeCount());
    }

    private static final class CheckoutFacade {

        private final InventoryService inventoryService;
        private final PaymentService paymentService;
        private final OrderService orderService;

        private CheckoutFacade(
                InventoryService inventoryService,
                PaymentService paymentService,
                OrderService orderService
        ) {
            this.inventoryService = inventoryService;
            this.paymentService = paymentService;
            this.orderService = orderService;
        }

        private String checkout(CheckoutCommand command) {
            Reservation reservation = inventoryService.reserve(command.sku(), command.quantity());
            if (!reservation.success()) {
                throw new IllegalStateException("未预留库存，结算已停止");
            }
            PaymentReceipt receipt = paymentService.charge(command.orderId(), command.amount());
            return orderService.create(command.orderId(), reservation, receipt);
        }
    }

    private static final class InventoryService {

        private final int availableStock;

        private InventoryService(int availableStock) {
            this.availableStock = availableStock;
        }

        private Reservation reserve(String sku, int quantity) {
            return new Reservation(availableStock >= quantity, sku, quantity);
        }
    }

    private static final class PaymentService {

        private int chargeCount;

        private PaymentReceipt charge(String orderId, BigDecimal amount) {
            chargeCount++;
            return new PaymentReceipt("PAY-" + orderId, amount);
        }

        private int chargeCount() {
            return chargeCount;
        }
    }

    private static final class OrderService {

        private String create(String orderId, Reservation reservation, PaymentReceipt receipt) {
            if (!reservation.success()) {
                throw new IllegalStateException("库存未预留");
            }
            return orderId + " 下单成功，支付凭证 " + receipt.paymentId();
        }
    }

    private record CheckoutCommand(String orderId, String sku, int quantity, BigDecimal amount) {
    }

    private record Reservation(boolean success, String sku, int quantity) {
    }

    private record PaymentReceipt(String paymentId, BigDecimal amount) {
    }
}
