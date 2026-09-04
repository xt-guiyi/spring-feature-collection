package com.xt.xiaoxingxing.playground.features.codedesign.modeling;

import java.util.ArrayList;
import java.util.List;

public final class DefensiveCopyDemo {

    private DefensiveCopyDemo() {
    }

    public static void main(String[] args) {
        List<String> sourceItems = new ArrayList<>(List.of("Java 书籍", "笔记本"));
        DirectOrder directOrder = new DirectOrder(new ArrayList<>(sourceItems));
        SafeOrder improvedOrder = new SafeOrder(sourceItems);

        System.out.println("直接写法商品数：" + directOrder.items().size());
        System.out.println("改进写法商品数：" + improvedOrder.items().size());
        System.out.println("业务结果一致："
                + (directOrder.items().size() == improvedOrder.items().size()));

        directOrder.items().clear();
        System.out.println("外部清空集合后，直接写法商品数：" + directOrder.items().size());

        try {
            improvedOrder.items().clear();
        } catch (UnsupportedOperationException exception) {
            System.out.println("防御性复制阻止外部修改订单明细");
        }

        sourceItems.add("鼠标垫");
        System.out.println("原始集合变化后，安全订单商品数仍为：" + improvedOrder.items().size());
    }

    private static final class DirectOrder {

        private final List<String> items;

        private DirectOrder(List<String> items) {
            this.items = items;
        }

        private List<String> items() {
            return items;
        }
    }

    private static final class SafeOrder {

        private final List<String> items;

        private SafeOrder(List<String> items) {
            this.items = List.copyOf(items);
        }

        private List<String> items() {
            return items;
        }
    }
}
