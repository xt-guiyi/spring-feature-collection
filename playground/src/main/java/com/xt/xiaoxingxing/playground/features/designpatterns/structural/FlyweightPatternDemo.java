package com.xt.xiaoxingxing.playground.features.designpatterns.structural;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 享元：大量订单明细共享不可变的 SKU 元数据。 */
public final class FlyweightPatternDemo {

    private FlyweightPatternDemo() {
    }

    public static void main(String[] args) {
        List<DirectOrderLine> directLines = List.of(
                directLine("SKU-BOOK", 1),
                directLine("SKU-BOOK", 2),
                directLine("SKU-BOOK", 1),
                directLine("SKU-NOTE", 2),
                directLine("SKU-NOTE", 3),
                directLine("SKU-NOTE", 1)
        );
        BigDecimal directTotal = directLines.stream()
                .map(DirectOrderLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long directMetadataCount = directLines.stream()
                .map(DirectOrderLine::metadata)
                .distinct()
                .count();

        SkuMetadataFactory factory = new SkuMetadataFactory();
        List<OrderLine> sharedLines = List.of(
                sharedLine(factory, "SKU-BOOK", 1),
                sharedLine(factory, "SKU-BOOK", 2),
                sharedLine(factory, "SKU-BOOK", 1),
                sharedLine(factory, "SKU-NOTE", 2),
                sharedLine(factory, "SKU-NOTE", 3),
                sharedLine(factory, "SKU-NOTE", 1)
        );
        BigDecimal patternTotal = sharedLines.stream()
                .map(OrderLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long sharedMetadataCount = sharedLines.stream()
                .map(OrderLine::metadata)
                .distinct()
                .count();

        System.out.println("直接写法：总额 " + directTotal
                + "，SKU 元数据实例 " + directMetadataCount);
        System.out.println("享元写法：总额 " + patternTotal
                + "，SKU 元数据实例 " + sharedMetadataCount);
        System.out.println("共享同一元数据实例："
                + (sharedLines.get(0).metadata() == sharedLines.get(1).metadata()));
        System.out.println("不同 SKU 使用不同实例："
                + (sharedLines.get(0).metadata() != sharedLines.get(3).metadata()));
        System.out.println("风险：元数据必须不可变，成交价、数量等外部状态留在订单明细中");
    }

    private static DirectOrderLine directLine(String sku, int quantity) {
        return new DirectOrderLine(
                new DirectSkuMetadata(sku, nameOf(sku)),
                priceOf(sku),
                quantity
        );
    }

    private static OrderLine sharedLine(SkuMetadataFactory factory, String sku, int quantity) {
        return new OrderLine(factory.get(sku), priceOf(sku), quantity);
    }

    private static String nameOf(String sku) {
        return switch (sku) {
            case "SKU-BOOK" -> "Java 后端实战";
            case "SKU-NOTE" -> "开发者笔记本";
            default -> throw new IllegalArgumentException("未知 SKU：" + sku);
        };
    }

    private static BigDecimal priceOf(String sku) {
        return switch (sku) {
            case "SKU-BOOK" -> new BigDecimal("99.00");
            case "SKU-NOTE" -> new BigDecimal("12.00");
            default -> throw new IllegalArgumentException("未知 SKU：" + sku);
        };
    }

    private static final class DirectSkuMetadata {

        private final String sku;
        private final String name;

        private DirectSkuMetadata(String sku, String name) {
            this.sku = sku;
            this.name = name;
        }
    }

    private record DirectOrderLine(
            DirectSkuMetadata metadata,
            BigDecimal unitPrice,
            int quantity
    ) {

        private BigDecimal amount() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    private static final class SkuMetadataFactory {

        private final Map<String, SkuMetadata> cache = new HashMap<>();

        private SkuMetadata get(String sku) {
            return cache.computeIfAbsent(sku, this::loadMetadata);
        }

        private SkuMetadata loadMetadata(String sku) {
            return new SkuMetadata(sku, nameOf(sku));
        }
    }

    /** 享元只保存可安全共享的内在状态。 */
    private static final class SkuMetadata {

        private final String sku;
        private final String name;

        private SkuMetadata(String sku, String name) {
            this.sku = sku;
            this.name = name;
        }
    }

    private record OrderLine(SkuMetadata metadata, BigDecimal unitPrice, int quantity) {

        private BigDecimal amount() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
