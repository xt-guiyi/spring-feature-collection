package com.xt.xiaoxingxing.playground.rocketmq.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(RocketMqLearningProperties.class)
public class RocketMqLearningConfig {
}
