package com.xt.xiaoxingxing.shared.infrastructure.rocketmq;

import org.apache.rocketmq.client.annotation.RocketMQMessageListenerBeanPostProcessor.AnnotationEnhancer;
import org.apache.rocketmq.client.autoconfigure.RocketMQProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** 项目统一的 RocketMQ 监听器配置。 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "shared.infrastructure.rocketmq",
        name = "enabled",
        havingValue = "true"
)
@ConditionalOnClass({RocketMQProperties.class, AnnotationEnhancer.class})
public class RocketMqAutoConfiguration {

    @Bean
    @ConditionalOnBean(RocketMQProperties.class)
    @ConditionalOnMissingBean(AnnotationEnhancer.class)
    public RocketMqListenerEnhancer rocketMqListenerEnhancer(RocketMQProperties properties) {
        return new RocketMqListenerEnhancer(properties);
    }
}
