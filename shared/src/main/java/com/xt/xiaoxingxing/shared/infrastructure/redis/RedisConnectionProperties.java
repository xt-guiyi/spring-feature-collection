package com.xt.xiaoxingxing.shared.infrastructure.redis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Redis 连接配置，供 Spring Data Redis 和 Redisson 共用。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisConnectionProperties {

    @NotBlank
    private String host;

    @NotNull
    @Min(1)
    @Max(65535)
    private Integer port;

    private String username;

    private String password;

    @NotNull
    @Min(0)
    private Integer database;

    @NotNull
    private Duration timeout;

    @NotNull
    private Duration connectTimeout;

    @Valid
    @NotNull
    private Ssl ssl;

    @AssertTrue(message = "spring.data.redis.timeout 和 connect-timeout 必须大于 0")
    public boolean isTimeoutConfigurationValid() {
        return isMissingOrPositive(timeout) && isMissingOrPositive(connectTimeout);
    }

    private boolean isMissingOrPositive(Duration value) {
        return value == null || (!value.isZero() && !value.isNegative());
    }

    @Getter
    @Setter
    public static class Ssl {

        @NotNull
        private Boolean enabled;
    }
}
