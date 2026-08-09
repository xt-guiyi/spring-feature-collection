package com.xt.xiaoxingxing.playground.redis.service.impl;

import com.xt.xiaoxingxing.playground.redis.service.LockDemoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LockDemoServiceImpl implements LockDemoService {

    private final RedissonClient redissonClient;

    @Override
    public String lockDemo(String resourceKey) {
        return lockDemo(resourceKey, 3, 10);
    }

    @Override
    public String lockDemo(String resourceKey, long waitTime, long leaseTime) {
        String lockKey = "lock:" + resourceKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (locked) {
                try {
                    log.info("线程 [{}] 获取到锁 {}", Thread.currentThread().getName(), lockKey);
                    // 模拟业务处理
                    Thread.sleep(1000);
                    return "获取锁成功，处理完成";
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.info("线程 [{}] 释放锁 {}", Thread.currentThread().getName(), lockKey);
                    }
                }
            } else {
                log.warn("线程 [{}] 获取锁 {} 失败", Thread.currentThread().getName(), lockKey);
                return "获取锁失败，资源被占用";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "线程被中断";
        }
    }
}
