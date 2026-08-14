package com.xt.xiaoxingxing.playground.redis.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 将 Redis 学习模块自己的配置对象注册为 Spring Bean。 */
@Configuration
@EnableConfigurationProperties(RedisLockProperties.class)
public class RedisLearningConfig {
}
