package com.xt.xiaoxingxing.playground.features.basics;

import java.util.ArrayList;
import java.util.List;

/** 泛型基础：泛型类、泛型方法和上下界通配符。 */
public final class GenericsDemo {

    private GenericsDemo() {
    }

    public static void main(String[] args) {
        Box<String> topicBox = new Box<>("Java 泛型");
        List<Integer> scores = List.of(88, 92, 76);

        System.out.println("泛型类保存的主题：" + topicBox.value());
        System.out.println("泛型方法取得首个分数：" + first(scores));
        System.out.println("上界通配符计算总分：" + sum(scores));

        List<Number> reviewScores = new ArrayList<>();
        addReviewScores(reviewScores);
        System.out.println("下界通配符写入分数：" + reviewScores);
    }

    private static <T> T first(List<T> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("列表不能为空");
        }
        return values.getFirst();
    }

    /** extends 表示可以安全读取为 Number。 */
    private static double sum(List<? extends Number> values) {
        double total = 0;
        for (Number value : values) {
            total += value.doubleValue();
        }
        return total;
    }

    /** super 表示可以安全写入 Integer。 */
    private static void addReviewScores(List<? super Integer> target) {
        target.add(60);
        target.add(80);
    }

    private static final class Box<T> {

        private final T value;

        private Box(T value) {
            this.value = value;
        }

        private T value() {
            return value;
        }
    }
}
