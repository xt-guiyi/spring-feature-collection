package com.xt.xiaoxingxing.playground.rocketmq.config;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.annotation.RocketMQMessageListenerBeanPostProcessor.AnnotationEnhancer;
import org.apache.rocketmq.client.autoconfigure.RocketMQProperties;
import org.springframework.stereotype.Component;

import java.lang.reflect.AnnotatedElement;
import java.util.Map;

/** 订单消息监听器配置增强器。 */
@Component
@RequiredArgsConstructor
public class OrderMqListenerEnhancer implements AnnotationEnhancer {

    private final RocketMQProperties rocketMqProperties;

    /** 设置监听器的 SSL 配置。 */
    @Override
    public Map<String, Object> apply(Map<String, Object> attributes, AnnotatedElement element) {
        attributes.put("sslEnabled", rocketMqProperties.getProducer().isSslEnabled());
        return attributes;
    }
}
