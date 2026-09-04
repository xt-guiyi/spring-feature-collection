package com.xt.xiaoxingxing.playground.features.designpatterns.structural;

import java.math.BigDecimal;

/** 代理：在保持退款接口不变的前提下增加权限控制。 */
public final class ProxyPatternDemo {

    private ProxyPatternDemo() {
    }

    public static void main(String[] args) {
        RefundCommand allowed = new RefundCommand("ORDER-1001", new BigDecimal("88.00"));
        Operator finance = new Operator("USER-01", Role.FINANCE);

        RealRefundService directService = new RealRefundService();
        String directResult = directRefund(directService, finance, allowed);

        RealRefundService realService = new RealRefundService();
        RefundService proxy = new PermissionRefundProxy(realService, finance);
        String patternResult = proxy.refund(allowed);

        System.out.println("直接写法：" + directResult);
        System.out.println("代理写法：" + patternResult);

        RealRefundService deniedService = new RealRefundService();
        Operator customerService = new Operator("USER-02", Role.CUSTOMER_SERVICE);
        RefundService protectedService = new PermissionRefundProxy(deniedService, customerService);
        try {
            protectedService.refund(new RefundCommand("ORDER-1002", new BigDecimal("20.00")));
        } catch (SecurityException exception) {
            System.out.println("无权限请求：" + exception.getMessage());
        }
        System.out.println("无权限请求到达真实服务：" + (deniedService.callCount() > 0));
    }

    private static String directRefund(
            RefundService service,
            Operator operator,
            RefundCommand command
    ) {
        // 权限判断放在每个调用方，容易被遗漏。
        if (operator.role() != Role.FINANCE) {
            throw new SecurityException("只有财务角色可以退款");
        }
        return service.refund(command);
    }

    private interface RefundService {

        String refund(RefundCommand command);
    }

    private static final class RealRefundService implements RefundService {

        private int callCount;

        @Override
        public String refund(RefundCommand command) {
            callCount++;
            return command.orderId() + " 退款 " + command.amount() + " 元成功";
        }

        private int callCount() {
            return callCount;
        }
    }

    /** 代理和真实服务实现同一个接口，调用方无需改变用法。 */
    private static final class PermissionRefundProxy implements RefundService {

        private final RefundService target;
        private final Operator authenticatedOperator;

        private PermissionRefundProxy(RefundService target, Operator authenticatedOperator) {
            this.target = target;
            this.authenticatedOperator = authenticatedOperator;
        }

        @Override
        public String refund(RefundCommand command) {
            if (authenticatedOperator.role() != Role.FINANCE) {
                throw new SecurityException("只有财务角色可以退款");
            }
            return target.refund(command);
        }
    }

    private record RefundCommand(String orderId, BigDecimal amount) {
    }

    /** 操作人来自独立的认证上下文，不由退款业务参数自行声明角色。 */
    private record Operator(String id, Role role) {
    }

    private enum Role {
        FINANCE, CUSTOMER_SERVICE
    }
}
