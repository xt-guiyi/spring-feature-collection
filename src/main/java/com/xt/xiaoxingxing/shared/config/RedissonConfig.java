package com.xt.xiaoxingxing.shared.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties({RedisConnectionProperties.class, RedisLockProperties.class})
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(RedisConnectionProperties properties) {
        /*
         * Redisson 不会自动复用 Spring Data Redis 已解析的连接对象，因此这里显式读取
         * 同一个 spring.data.redis 前缀。这样缓存客户端与分布式锁客户端始终指向同一 Redis。
         *
         * Redis 协议使用 redis://；启用 TLS 后必须改为 rediss://，否则即便端口和账号正确，
         * TLS 握手也不会发生。用户名、密码为空时不调用相应 setter，兼容未开启 ACL 的本地 Redis。
         */
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
}
