package com.xt.xiaoxingxing.playground.features.basics;

public final class StringDemo {

    private StringDemo() {
    }

    public static void main(String[] args) {
        String original = "Java";
        String changed = original.concat(" 21");

        // String 不可变，拼接会得到一个新字符串。
        System.out.println("原字符串：" + original);
        System.out.println("新字符串：" + changed);

        String first = new String("基础学习");
        String second = new String("基础学习");
        // 比较字符串内容使用 equals，不使用 ==。
        System.out.println("内容是否相同：" + first.equals(second));

        StringBuilder builder = new StringBuilder();
        builder.append("Java").append(" 基础").append(" 学习");
        System.out.println("构建结果：" + builder);
    }
}
