package com.xt.xiaoxingxing.playground.redis.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Redisson 分布式锁学习接口的默认时长。
 *
 * <p>接口仍允许调用方传入等待与租约秒数；本对象只定义未传参数时的演示默认行为。
 * 它没有 Java 默认值，便于在不同环境明确观察锁等待、自动释放和模拟业务耗时的关系。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "playground.redis.lock")
public class RedisLockProperties {

    @NotNull
    private Duration waitTime;

    @NotNull
    private Duration leaseTime;

    @NotNull
    private Duration simulatedWorkDuration;

    /**
     * 等待时间允许为 0（表示只尝试一次），但租约必须为正；模拟业务时长允许为 0，不能为负数。
     * 缺失值继续交给字段上的 @NotNull 提示，避免这里产生空指针。
     */
    @AssertTrue(message = "Redis锁配置要求 wait-time>=0、lease-time>0、simulated-work-duration>=0")
    public boolean isDurationConfigurationValid() {
        return isMissingOrNonNegative(waitTime)
                && (leaseTime == null || (!leaseTime.isZero() && !leaseTime.isNegative()))
                && isMissingOrNonNegative(simulatedWorkDuration);
    }

    private boolean isMissingOrNonNegative(Duration value) {
        return value == null || !value.isNegative();
    }
}
