package com.xt.xiaoxingxing.playground.redis.service.impl;

import com.xt.xiaoxingxing.playground.redis.config.RedisLockProperties;
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
    private final RedisLockProperties lockProperties;

    @Override
    public String lockDemo(String resourceKey) {
        /*
         * 无参演示接口采用 YAML 中的默认值；有参重载仍保留，方便学习时直接观察不同等待、租约时长的结果。
         * Duration 在配置中可写成 3s、500ms 等可读形式。这里以毫秒传给 Redisson，避免 500ms
         * 被 toSeconds() 截断成 0；有参重载的历史语义仍明确按“秒”处理。
         */
        return executeLock(resourceKey,
                lockProperties.getWaitTime().toMillis(),
                lockProperties.getLeaseTime().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public String lockDemo(String resourceKey, long waitTime, long leaseTime) {
        return executeLock(resourceKey, waitTime, leaseTime, TimeUnit.SECONDS);
    }

    /**
     * 统一锁实现：无参入口使用 YAML 的毫秒精度；显式参数入口延续接口约定，单位为秒。
     * Redisson 会把等待时间和租约一起交给 Redis；租约到期后即使业务线程还在运行，锁也可能被其他线程获取。
     */
    private String executeLock(String resourceKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        String lockKey = "lock:" + resourceKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(waitTime, leaseTime, timeUnit);
            if (locked) {
                try {
                    log.info("线程 [{}] 获取到锁 {}", Thread.currentThread().getName(), lockKey);
                    // 模拟业务处理时长也外置，便于演示“业务执行超过租约”时锁自动释放的风险。
                    Thread.sleep(lockProperties.getSimulatedWorkDuration().toMillis());
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
