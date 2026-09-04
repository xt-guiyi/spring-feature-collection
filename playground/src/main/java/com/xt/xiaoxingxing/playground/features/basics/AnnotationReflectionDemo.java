package com.xt.xiaoxingxing.playground.features.basics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

public final class AnnotationReflectionDemo {

    private AnnotationReflectionDemo() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        GreetingService service = new GreetingService();
        Method method = GreetingService.class.getDeclaredMethod("greet", String.class);

        LearningOperation operation = method.getAnnotation(LearningOperation.class);
        System.out.println("注解说明：" + operation.value());

        // 反射调用时，参数和返回值会在运行时检查。
        Object result = method.invoke(service, "小星星");
        System.out.println("反射结果：" + result);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface LearningOperation {

        String value();
    }

    private static final class GreetingService {

        @LearningOperation("生成问候语")
        public String greet(String name) {
            return "你好，" + name;
        }
    }
}
