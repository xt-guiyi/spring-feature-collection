package com.xt.xiaoxingxing.playground.redis.service;

public interface LockDemoService {

    /**
     * 尝试对指定资源加锁并执行业务逻辑
     *
     * @param resourceKey 资源标识
     * @return 加锁结果描述
     */
    String lockDemo(String resourceKey);

    /**
     * 尝试对指定资源加锁并执行业务逻辑
     *
     * @param resourceKey 资源标识
     * @param waitTime    获取锁的最大等待时间，单位秒；0 表示不等待
     * @param leaseTime   锁自动释放时间，单位秒
     * @return 加锁结果描述
     */
    String lockDemo(String resourceKey, long waitTime, long leaseTime);
}
