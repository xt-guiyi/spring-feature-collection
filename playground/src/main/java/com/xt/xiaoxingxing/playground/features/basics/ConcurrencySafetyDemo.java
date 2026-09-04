package com.xt.xiaoxingxing.playground.features.basics;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class ConcurrencySafetyDemo {

    private ConcurrencySafetyDemo() {
    }

    public static void main(String[] args) {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            int unsafeResult = runLostUpdate(executor);
            int synchronizedResult = runTwice(executor, new SynchronizedCounter());
            int lockResult = runTwice(executor, new LockCounter());
            int atomicResult = runTwice(executor, new AtomicCounter());

            System.out.printf("普通 int：期望 2，实际 %d（丢失更新）%n", unsafeResult);
            System.out.printf("synchronized：期望 2，实际 %d%n", synchronizedResult);
            System.out.printf("ReentrantLock：期望 2，实际 %d%n", lockResult);
            System.out.printf("AtomicInteger：期望 2，实际 %d%n", atomicResult);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            System.err.println("主线程被中断");
        } catch (ExecutionException exception) {
            throw new IllegalStateException("并发任务执行失败", exception.getCause());
        }
    }

    private static int runLostUpdate(ExecutorService executor)
            throws InterruptedException, ExecutionException {
        UnsafeCounter counter = new UnsafeCounter();
        CountDownLatch bothHaveRead = new CountDownLatch(2);
        CountDownLatch allowWrite = new CountDownLatch(1);

        Runnable incrementTask = () -> {
            try {
                counter.increment(bothHaveRead, allowWrite);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("计数任务被中断", exception);
            }
        };

        Future<?> first = executor.submit(incrementTask);
        Future<?> second = executor.submit(incrementTask);
        try {
            // 两个线程都先读到 0，再一起写入 1，稳定复现 lost update。
            bothHaveRead.await();
        } finally {
            allowWrite.countDown();
        }
        first.get();
        second.get();
        return counter.value();
    }

    private static int runTwice(ExecutorService executor, Counter counter)
            throws InterruptedException, ExecutionException {
        Future<?> first = executor.submit(counter::increment);
        Future<?> second = executor.submit(counter::increment);
        first.get();
        second.get();
        return counter.value();
    }

    private interface Counter {

        void increment();

        int value();
    }

    private static final class UnsafeCounter {

        private int value;

        public void increment(CountDownLatch bothHaveRead, CountDownLatch allowWrite)
                throws InterruptedException {
            int current = value;
            bothHaveRead.countDown();
            allowWrite.await();
            value = current + 1;
        }

        public int value() {
            return value;
        }
    }

    private static final class SynchronizedCounter implements Counter {

        private int value;

        @Override
        public synchronized void increment() {
            value++;
        }

        @Override
        public synchronized int value() {
            return value;
        }
    }

    private static final class LockCounter implements Counter {

        private final Lock lock = new ReentrantLock();
        private int value;

        @Override
        public void increment() {
            lock.lock();
            try {
                value++;
            } finally {
                lock.unlock();
            }
        }

        @Override
        public int value() {
            lock.lock();
            try {
                return value;
            } finally {
                lock.unlock();
            }
        }
    }

    private static final class AtomicCounter implements Counter {

        private final AtomicInteger value = new AtomicInteger();

        @Override
        public void increment() {
            value.incrementAndGet();
        }

        @Override
        public int value() {
            return value.get();
        }
    }
}
