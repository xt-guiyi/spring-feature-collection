package com.xt.xiaoxingxing.playground.redis;

import com.xt.xiaoxingxing.playground.redis.service.LockDemoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LockDemoService 集成测试。
 * <p>
 * 运行前请确保本地 Redis 服务已启动（默认 localhost:6379）。
 */
@SpringBootTest
class LockDemoServiceTest {

    @Autowired
    private LockDemoService lockDemoService;

    @Test
    void testLockSuccess() {
        String result = lockDemoService.lockDemo("order-1");
        assertTrue(result.contains("成功"));
    }

    @Test
    void testConcurrentLock() throws InterruptedException {
        String resourceKey = "order-2";
        int threadCount = 2;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        Runnable task = () -> {
            // waitTime 传 0，非阻塞获取锁，用于验证互斥
            results.add(lockDemoService.lockDemo(resourceKey, 0, 10));
            latch.countDown();
        };

        for (int i = 0; i < threadCount; i++) {
            new Thread(task).start();
        }

        latch.await();

        long successCount = results.stream().filter(r -> r.contains("成功")).count();
        long failCount = results.stream().filter(r -> r.contains("失败")).count();

        assertEquals(1, successCount, "并发场景下应只有一个线程获取到锁");
        assertEquals(1, failCount, "并发场景下应只有一个线程获取锁失败");
    }
}
