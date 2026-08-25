package com.xt.xiaoxingxing.playground.rocketmq.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 订单消息配置。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OrderMqProperties.class)
public class OrderMqConfig {
}
