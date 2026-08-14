package com.xt.xiaoxingxing.shared.config;

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

/**
 * Redis 连接配置。
 *
 * <p>这个类刻意不提供 Java 默认值：连接到哪台 Redis、是否启用 TLS、超时多久，
 * 都是运行环境的部署参数，应当由 application.yaml / application-dev.yaml 明确声明。
 * Spring Boot 的 Redis 自动配置与 Redisson 配置共用 {@code spring.data.redis} 前缀，
 * 防止两套客户端意外连接到不同实例。</p>
 */
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

    /** Redisson 的命令与建连超时都必须为正数；缺失值由各字段的 @NotNull 单独提示。 */
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
