package com.xt.xiaoxingxing.playground.features.designpatterns.structural;

import java.math.BigDecimal;
import java.util.List;

/** 组合：让单品与任意层级的商品套餐使用相同的结算接口。 */
public final class CompositePatternDemo {

    private CompositePatternDemo() {
    }

    public static void main(String[] args) {
        Object directGiftBox = new DirectBundle(List.of(
                new DirectProduct(new BigDecimal("80.00")),
                new DirectProduct(new BigDecimal("20.00"))
        ));
        Object directFestivalBundle = new DirectBundle(List.of(
                directGiftBox,
                new DirectProduct(new BigDecimal("5.00"))
        ));
        BigDecimal directTotal = directPrice(directFestivalBundle);

        PriceComponent giftBox = new Bundle("茶杯礼盒", List.of(
                new Product("茶叶", new BigDecimal("80.00")),
                new Product("茶杯", new BigDecimal("20.00"))
        ));
        PriceComponent festivalBundle = new Bundle("节日套餐", List.of(
                giftBox,
                new Product("贺卡", new BigDecimal("5.00"))
        ));

        System.out.println("直接写法：节日套餐总价 " + directTotal);
        System.out.println("组合写法：" + settle(festivalBundle));
        System.out.println("同一接口也能结算单品："
                + settle(new Product("单独茶杯", new BigDecimal("20.00"))));
    }

    private static BigDecimal directPrice(Object component) {
        if (component instanceof DirectProduct product) {
            return product.price();
        }
        if (component instanceof DirectBundle bundle) {
            return bundle.children().stream()
                    .map(CompositePatternDemo::directPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        throw new IllegalArgumentException("未知商品节点");
    }

    private static String settle(PriceComponent component) {
        return component.name() + "总价 " + component.price();
    }

    private record DirectProduct(BigDecimal price) {
    }

    private record DirectBundle(List<Object> children) {

        private DirectBundle {
            children = List.copyOf(children);
        }
    }

    private interface PriceComponent {

        String name();

        BigDecimal price();
    }

    private record Product(String name, BigDecimal price) implements PriceComponent {
    }

    private record Bundle(String name, List<PriceComponent> children) implements PriceComponent {

        private Bundle {
            children = List.copyOf(children);
        }

        @Override
        public BigDecimal price() {
            return children.stream()
                    .map(PriceComponent::price)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }
}
