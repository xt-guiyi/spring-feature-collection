package com.xt.xiaoxingxing.playground.features.basics;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class JdkAsyncDemo {

    private JdkAsyncDemo() {
    }

    public static void main(String[] args) {
        System.out.println("调用线程：" + Thread.currentThread().getName());

        try {
            runPlatformThread();
            runFuture();
            runCompletableFuture();
            runVirtualThread();
        } catch (InterruptedException exception) {
            // InterruptedException 会清除中断标记，捕获后应恢复。
            Thread.currentThread().interrupt();
            System.err.println("主线程被中断");
        } catch (ExecutionException exception) {
            throw new IllegalStateException("异步任务执行失败", exception.getCause());
        }
    }

    private static void runPlatformThread() throws InterruptedException {
        Thread thread = new Thread(
                () -> System.out.println("Thread 执行线程：" + Thread.currentThread().getName()),
                "basic-platform-thread"
        );
        thread.start();
        thread.join();
    }

    private static void runFuture() throws InterruptedException, ExecutionException {
        // Java 21 中 ExecutorService 可用 try-with-resources 自动关闭。
        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
            Future<String> future = executor.submit(
                    () -> "Future 执行线程：" + Thread.currentThread().getName()
            );
            System.out.println(future.get());
        }
    }

    private static void runCompletableFuture() throws InterruptedException, ExecutionException {
        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(
                    () -> "CompletableFuture 执行线程：" + Thread.currentThread().getName(),
                    executor
            );
            System.out.println(future.get());
        }
    }

    private static void runVirtualThread() throws InterruptedException {
        Thread thread = Thread.ofVirtual()
                .name("basic-virtual-thread")
                .start(() -> System.out.println(
                        "虚拟线程：" + Thread.currentThread().getName()
                                + "，isVirtual=" + Thread.currentThread().isVirtual()
                ));
        thread.join();
    }
}
