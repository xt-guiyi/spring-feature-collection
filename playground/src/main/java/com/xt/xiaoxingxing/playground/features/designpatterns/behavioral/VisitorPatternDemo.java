package com.xt.xiaoxingxing.playground.features.designpatterns.behavioral;

import java.math.BigDecimal;
import java.util.List;

/** 访问者模式：为稳定的商品类型增加运费、发票等新操作。 */
public final class VisitorPatternDemo {

    private VisitorPatternDemo() {
    }

    public static void main(String[] args) {
        List<OrderItem> items = List.of(
                new PhysicalItem("Java 书籍", new BigDecimal("89.00")),
                new DigitalItem("Java 课程", new BigDecimal("199.00")),
                new ServiceItem("安装服务", new BigDecimal("50.00"))
        );

        BigDecimal directShippingFee = shippingFeeDirectly(items);
        ShippingFeeVisitor shippingVisitor = new ShippingFeeVisitor();
        BigDecimal patternShippingFee = items.stream()
                .map(item -> item.accept(shippingVisitor))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        InvoiceLineVisitor invoiceVisitor = new InvoiceLineVisitor();
        List<String> invoiceLines = items.stream()
                .map(item -> item.accept(invoiceVisitor))
                .toList();

        System.out.println("直接写法运费：" + directShippingFee);
        System.out.println("访问者模式运费：" + patternShippingFee);
        System.out.println("业务结果一致：" + (directShippingFee.compareTo(patternShippingFee) == 0));
        System.out.println("增加发票展示操作：" + invoiceLines);
    }

    private static BigDecimal shippingFeeDirectly(List<OrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            if (item instanceof PhysicalItem) {
                total = total.add(new BigDecimal("10.00"));
            } else if (item instanceof DigitalItem || item instanceof ServiceItem) {
                total = total.add(BigDecimal.ZERO);
            }
        }
        return total;
    }

    private interface OrderItem {

        <R> R accept(OrderItemVisitor<R> visitor);
    }

    private interface OrderItemVisitor<R> {

        R visit(PhysicalItem item);

        R visit(DigitalItem item);

        R visit(ServiceItem item);
    }

    private record PhysicalItem(String name, BigDecimal price) implements OrderItem {

        @Override
        public <R> R accept(OrderItemVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    private record DigitalItem(String name, BigDecimal price) implements OrderItem {

        @Override
        public <R> R accept(OrderItemVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    private record ServiceItem(String name, BigDecimal price) implements OrderItem {

        @Override
        public <R> R accept(OrderItemVisitor<R> visitor) {
            return visitor.visit(this);
        }
    }

    private static final class ShippingFeeVisitor implements OrderItemVisitor<BigDecimal> {

        @Override
        public BigDecimal visit(PhysicalItem item) {
            return new BigDecimal("10.00");
        }

        @Override
        public BigDecimal visit(DigitalItem item) {
            return BigDecimal.ZERO;
        }

        @Override
        public BigDecimal visit(ServiceItem item) {
            return BigDecimal.ZERO;
        }
    }

    private static final class InvoiceLineVisitor implements OrderItemVisitor<String> {

        @Override
        public String visit(PhysicalItem item) {
            return "实物：" + item.name() + "，" + item.price();
        }

        @Override
        public String visit(DigitalItem item) {
            return "数字商品：" + item.name() + "，" + item.price();
        }

        @Override
        public String visit(ServiceItem item) {
            return "服务：" + item.name() + "，" + item.price();
        }
    }
}
