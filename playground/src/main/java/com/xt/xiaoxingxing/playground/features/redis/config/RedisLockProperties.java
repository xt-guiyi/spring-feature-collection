package com.xt.xiaoxingxing.playground.features.redis.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Redis 学习模块的分布式锁参数。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.redis.lock")
public class RedisLockProperties {

    @NotNull
    private Duration waitTime;

    @NotNull
    private Duration leaseTime;

    @NotNull
    private Duration simulatedWorkDuration;

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
