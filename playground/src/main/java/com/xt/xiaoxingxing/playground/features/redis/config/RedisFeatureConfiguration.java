package com.xt.xiaoxingxing.playground.features.redis.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Redis 学习模块的业务参数配置。 */
@Configuration
@EnableConfigurationProperties(RedisLockProperties.class)
public class RedisFeatureConfiguration {
}
