package com.xt.xiaoxingxing.playground.features.codedesign.modeling;

public final class ValidateBeforeMutateDemo {

    private ValidateBeforeMutateDemo() {
    }

    public static void main(String[] args) {
        DirectOrder directOrder = new DirectOrder("上海市", 1);
        directOrder.changeDelivery("北京市", 3);
        SafeOrder improvedOrder = new SafeOrder("上海市", 1);
        improvedOrder.changeDelivery("北京市", 3);

        System.out.println("直接写法：" + directOrder.snapshot());
        System.out.println("改进写法：" + improvedOrder.snapshot());
        System.out.println("业务结果一致：" + directOrder.snapshot().equals(improvedOrder.snapshot()));

        DirectOrder invalidDirectOrder = new DirectOrder("上海市", 1);
        try {
            invalidDirectOrder.changeDelivery("广州市", 0);
        } catch (IllegalArgumentException exception) {
            System.out.println("直接写法失败后已被部分修改：" + invalidDirectOrder.snapshot());
        }

        SafeOrder invalidImprovedOrder = new SafeOrder("上海市", 1);
        try {
            invalidImprovedOrder.changeDelivery("广州市", 0);
        } catch (IllegalArgumentException exception) {
            System.out.println("先校验再修改，失败后仍为：" + invalidImprovedOrder.snapshot());
        }
    }

    private record DeliverySnapshot(String address, int quantity) {
    }

    private static final class DirectOrder {

        private String address;
        private int quantity;

        private DirectOrder(String address, int quantity) {
            this.address = address;
            this.quantity = quantity;
        }

        private void changeDelivery(String newAddress, int newQuantity) {
            if (newAddress == null || newAddress.isBlank()) {
                throw new IllegalArgumentException("地址不能为空");
            }
            address = newAddress;
            if (newQuantity <= 0) {
                throw new IllegalArgumentException("数量必须大于零");
            }
            quantity = newQuantity;
        }

        private DeliverySnapshot snapshot() {
            return new DeliverySnapshot(address, quantity);
        }
    }

    private static final class SafeOrder {

        private String address;
        private int quantity;

        private SafeOrder(String address, int quantity) {
            this.address = address;
            this.quantity = quantity;
        }

        private void changeDelivery(String newAddress, int newQuantity) {
            if (newAddress == null || newAddress.isBlank() || newQuantity <= 0) {
                throw new IllegalArgumentException("地址不能为空且数量必须大于零");
            }
            address = newAddress;
            quantity = newQuantity;
        }

        private DeliverySnapshot snapshot() {
            return new DeliverySnapshot(address, quantity);
        }
    }
}
