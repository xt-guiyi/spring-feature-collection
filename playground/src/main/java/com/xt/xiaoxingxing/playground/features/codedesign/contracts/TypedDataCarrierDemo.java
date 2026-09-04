package com.xt.xiaoxingxing.playground.features.codedesign.contracts;

import java.util.HashMap;
import java.util.Map;

/** 用明确类型传递数据，让字段名和字段类型在编译期可见。 */
public final class TypedDataCarrierDemo {

    private TypedDataCarrierDemo() {
    }

    public static void main(String[] args) {
        Map<String, Object> directData = new HashMap<>();
        directData.put("orderId", "O-1001");
        directData.put("amount", 100);

        OrderData improvedData = new OrderData("O-1001", 100);
        System.out.println("直接写法：" + summarizeDirect(directData));
        System.out.println("改进写法：" + summarizeImproved(improvedData));

        directData.put("amount", "100");
        try {
            summarizeDirect(directData);
        } catch (ClassCastException exception) {
            System.out.println("Map 类型错误只能在运行时发现：" + exception.getClass().getSimpleName());
        }
    }

    private static String summarizeDirect(Map<String, Object> data) {
        String orderId = (String) data.get("orderId");
        int amount = (Integer) data.get("amount");
        return "%s，应付 %d 元".formatted(orderId, amount);
    }

    private static String summarizeImproved(OrderData data) {
        return "%s，应付 %d 元".formatted(data.orderId(), data.amount());
    }

    private record OrderData(String orderId, int amount) {
    }
}
