package com.xt.xiaoxingxing.playground.features.designpatterns.behavioral;

import java.util.ArrayList;
import java.util.List;

/** 模板方法模式：固定履约骨架，由子类实现实物和虚拟商品的变化步骤。 */
public final class TemplateMethodPatternDemo {

    private TemplateMethodPatternDemo() {
    }

    public static void main(String[] args) {
        Order physicalOrder = new Order("ORDER-1001", "Java 书籍");
        List<String> directResult = fulfillPhysicalDirectly(physicalOrder);
        List<String> patternResult = new PhysicalFulfillment().fulfill(physicalOrder);

        System.out.println("直接写法：" + directResult);
        System.out.println("模板方法：" + patternResult);
        System.out.println("业务结果一致：" + directResult.equals(patternResult));

        Order digitalOrder = new Order("ORDER-1002", "Java 课程兑换码");
        System.out.println("复用骨架履约虚拟商品：" + new DigitalFulfillment().fulfill(digitalOrder));
    }

    private static List<String> fulfillPhysicalDirectly(Order order) {
        List<String> steps = new ArrayList<>();
        steps.add("校验订单：" + order.id());
        steps.add("预占库存：" + order.productName());
        steps.add("创建物流单：" + order.id());
        steps.add("记录履约完成：" + order.id());
        return List.copyOf(steps);
    }

    private abstract static class FulfillmentFlow {

        // 模板方法固定执行顺序，子类只实现变化步骤。
        public final List<String> fulfill(Order order) {
            List<String> steps = new ArrayList<>();
            steps.add("校验订单：" + order.id());
            prepare(order, steps);
            deliver(order, steps);
            steps.add("记录履约完成：" + order.id());
            return List.copyOf(steps);
        }

        protected abstract void prepare(Order order, List<String> steps);

        protected abstract void deliver(Order order, List<String> steps);
    }

    private static final class PhysicalFulfillment extends FulfillmentFlow {

        @Override
        protected void prepare(Order order, List<String> steps) {
            steps.add("预占库存：" + order.productName());
        }

        @Override
        protected void deliver(Order order, List<String> steps) {
            steps.add("创建物流单：" + order.id());
        }
    }

    private static final class DigitalFulfillment extends FulfillmentFlow {

        @Override
        protected void prepare(Order order, List<String> steps) {
            steps.add("生成兑换码：" + order.productName());
        }

        @Override
        protected void deliver(Order order, List<String> steps) {
            steps.add("发送兑换码：" + order.id());
        }
    }

    private record Order(String id, String productName) {
    }
}
