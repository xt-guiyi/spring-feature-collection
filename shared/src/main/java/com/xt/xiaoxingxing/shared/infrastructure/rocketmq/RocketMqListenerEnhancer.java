package com.xt.xiaoxingxing.shared.infrastructure.rocketmq;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.annotation.RocketMQMessageListenerBeanPostProcessor.AnnotationEnhancer;
import org.apache.rocketmq.client.autoconfigure.RocketMQProperties;

import java.lang.reflect.AnnotatedElement;
import java.util.Map;

/** 将 RocketMQ 公共连接配置应用到各服务的消息监听器。 */
@RequiredArgsConstructor
public class RocketMqListenerEnhancer implements AnnotationEnhancer {

    private final RocketMQProperties rocketMqProperties;

    @Override
    public Map<String, Object> apply(Map<String, Object> attributes, AnnotatedElement element) {
        attributes.put("sslEnabled", rocketMqProperties.getProducer().isSslEnabled());
        return attributes;
    }
}
