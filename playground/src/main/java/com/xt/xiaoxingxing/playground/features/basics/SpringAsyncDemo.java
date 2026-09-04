package com.xt.xiaoxingxing.playground.features.basics;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class SpringAsyncDemo {

    private SpringAsyncDemo() {
    }

    public static void main(String[] args) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("basics-demo");
            context.register(AsyncConfiguration.class);
            context.refresh();

            AsyncLearningService service = context.getBean(AsyncLearningService.class);

            System.out.println("调用线程：" + Thread.currentThread().getName());
            System.out.println("容器中的代理类型：" + service.getClass().getName());

            // 必须通过容器取得的代理调用，直接 new 会让 @Async 失效。
            CompletableFuture<String> future = service.execute();
            System.out.println(future.get());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("主线程被中断");
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Spring 异步任务执行失败", exception.getCause());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @Profile("basics-demo")
    public static class AsyncConfiguration {

        @Bean(name = "basicsExecutor")
        public ThreadPoolTaskExecutor basicsExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(10);
            executor.setThreadNamePrefix("basics-spring-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(5);
            return executor;
        }

        @Bean
        public AsyncLearningService asyncLearningService() {
            return new AsyncLearningService();
        }
    }

    public static class AsyncLearningService {

        @Async("basicsExecutor")
        public CompletableFuture<String> execute() {
            return CompletableFuture.completedFuture(
                    "@Async 执行线程：" + Thread.currentThread().getName()
            );
        }
    }
}
