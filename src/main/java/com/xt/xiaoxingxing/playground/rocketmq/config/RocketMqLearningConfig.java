package com.xt.xiaoxingxing.playground.rocketmq.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 学习模块的 Spring 开关。
 *
 * <p>不在这里手工创建 {@code RocketMQClientTemplate}：它应由官方 v5 Starter 自动配置，业务代码只依赖
 * 发布适配器。这样将来遇到 Starter 与 Spring Boot 的兼容问题时，替换影响面也被限制在基础设施层。</p>
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RocketMqLearningProperties.class)
public class RocketMqLearningConfig {
}
