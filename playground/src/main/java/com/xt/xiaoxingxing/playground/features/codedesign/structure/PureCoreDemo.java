package com.xt.xiaoxingxing.playground.features.codedesign.structure;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class PureCoreDemo {

    private static final BigDecimal VIP_RATE = new BigDecimal("0.90");

    private PureCoreDemo() {
    }

    public static void main(String[] args) {
        InMemoryOrderRepository directRepository = new InMemoryOrderRepository();
        BigDecimal directPayable = directCheckout(
                directRepository, "ORDER-1001", new BigDecimal("600.00"), true
        );

        InMemoryOrderRepository improvedRepository = new InMemoryOrderRepository();
        CheckoutService checkoutService = new CheckoutService(improvedRepository);
        BigDecimal improvedPayable = checkoutService.checkout(
                "ORDER-1001", new BigDecimal("600.00"), true
        );

        System.out.println("直接写法应付金额：" + directPayable);
        System.out.println("改进写法应付金额：" + improvedPayable);
    }

    private static BigDecimal directCheckout(
            OrderRepository repository, String orderId, BigDecimal total, boolean vip
    ) {
        // 计算和保存耦合在一个方法里，想单独验证计算就必须准备仓储。
        BigDecimal payable = vip ? total.multiply(VIP_RATE) : total;
        repository.save(orderId, payable);
        return payable;
    }

    private static BigDecimal calculatePayable(BigDecimal total, boolean vip) {
        // 纯计算只由输入决定，不读取或修改外部状态。
        return vip ? total.multiply(VIP_RATE) : total;
    }

    private static final class CheckoutService {

        private final OrderRepository repository;

        private CheckoutService(OrderRepository repository) {
            this.repository = repository;
        }

        private BigDecimal checkout(String orderId, BigDecimal total, boolean vip) {
            BigDecimal payable = calculatePayable(total, vip);
            repository.save(orderId, payable);
            return payable;
        }
    }

    private interface OrderRepository {

        void save(String orderId, BigDecimal payable);
    }

    private static final class InMemoryOrderRepository implements OrderRepository {

        private final Map<String, BigDecimal> orders = new HashMap<>();

        @Override
        public void save(String orderId, BigDecimal payable) {
            orders.put(orderId, payable);
        }
    }
}
