package com.xt.xiaoxingxing.playground.features.designpatterns.creational;

import java.util.Map;

/** 单例模式：在当前应用类加载器内共享一份只读货币元数据。 */
public final class SingletonPatternDemo {

    private SingletonPatternDemo() {
    }

    public static void main(String[] args) {
        DirectCurrencyCatalog pricingCatalog = new DirectCurrencyCatalog();
        DirectCurrencyCatalog refundCatalog = new DirectCurrencyCatalog();
        System.out.println("直接写法是否复用目录：" + (pricingCatalog == refundCatalog));
        System.out.println("直接写法查询 CNY：" + pricingCatalog.scaleOf("CNY"));

        CurrencyCatalog first = CurrencyCatalog.INSTANCE;
        CurrencyCatalog second = CurrencyCatalog.INSTANCE;
        System.out.println("单例写法是否复用目录：" + (first == second));
        System.out.println("CNY 小数位数：" + first.scaleOf("CNY"));

        // 单例不跨类加载器和进程，不要用它保存订单、库存等可变业务状态。
        System.out.println("适用边界：当前应用类加载器内、只读共享数据");
    }

    private static final class DirectCurrencyCatalog {

        private final Map<String, Integer> scales = Map.of("CNY", 2, "JPY", 0);

        private int scaleOf(String currency) {
            Integer scale = scales.get(currency);
            if (scale == null) {
                throw new IllegalArgumentException("不支持的货币：" + currency);
            }
            return scale;
        }
    }

    /** Singleton：枚举天然保证每个类加载器中只有一个实例。 */
    private enum CurrencyCatalog {
        INSTANCE;

        private final Map<String, Integer> scales = Map.of("CNY", 2, "JPY", 0);

        private int scaleOf(String currency) {
            Integer scale = scales.get(currency);
            if (scale == null) {
                throw new IllegalArgumentException("不支持的货币：" + currency);
            }
            return scale;
        }
    }
}
