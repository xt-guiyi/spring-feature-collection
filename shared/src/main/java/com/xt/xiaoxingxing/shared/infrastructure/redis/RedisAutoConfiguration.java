package com.xt.xiaoxingxing.shared.infrastructure.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/** 项目统一的 Redis 和 Redisson 客户端配置。 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "shared.infrastructure.redis",
        name = "enabled",
        havingValue = "true"
)
@ConditionalOnClass({StringRedisTemplate.class, RedissonClient.class, JsonMapper.class})
@EnableConfigurationProperties(RedisConnectionProperties.class)
public class RedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(RedisConnectionProperties properties) {
        Config config = new Config();
        String scheme = Boolean.TRUE.equals(properties.getSsl().getEnabled()) ? "rediss" : "redis";
        SingleServerConfig singleServer = config.useSingleServer()
                .setAddress(scheme + "://" + properties.getHost() + ":" + properties.getPort())
                .setDatabase(properties.getDatabase())
                .setTimeout(Math.toIntExact(properties.getTimeout().toMillis()))
                .setConnectTimeout(Math.toIntExact(properties.getConnectTimeout().toMillis()));

        if (StringUtils.hasText(properties.getUsername())) {
            singleServer.setUsername(properties.getUsername());
        }
        if (StringUtils.hasText(properties.getPassword())) {
            singleServer.setPassword(properties.getPassword());
        }
        return Redisson.create(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisUtil redisUtil(StringRedisTemplate stringRedisTemplate, JsonMapper jsonMapper) {
        return new RedisUtil(stringRedisTemplate, jsonMapper);
    }
}
