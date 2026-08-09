package com.xt.xiaoxingxing.playground.rabbitmq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RabbitMQ 学习模块自己的业务参数。
 *
 * <p>{@code spring.rabbitmq.*} 负责 Spring Boot AMQP 连接；本类只保存业务层需要读取的延迟、重试、
 * Confirm 等参数。两者分开后，不会把连接配置和订单规则混在一起。</p>
 */
@Data
@ConfigurationProperties(prefix = "playground.rabbitmq")
public class RabbitMqLearningProperties {

    private long orderTimeoutMillis = 30 * 60 * 1000L;
    private long retryDelayMillis = 5_000L;
    private int maxConsumeRetries = 3;
    private long confirmTimeoutSeconds = 5L;
    private Outbox outbox = new Outbox();
    private Stream stream = new Stream();

    @Data
    public static class Outbox {
        private int batchSize = 20;
        private long fixedDelayMillis = 3_000L;
        private long lockTimeoutSeconds = 60L;
        private int maxPublishRetries = 10;
    }

    @Data
    public static class Stream {
        private String host = "localhost";
        private int port = 5552;
        private String username = "root";
        private String password = "123456";
    }
}
