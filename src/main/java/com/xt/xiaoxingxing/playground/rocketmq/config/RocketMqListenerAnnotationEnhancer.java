package com.xt.xiaoxingxing.playground.rocketmq.config;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.annotation.RocketMQMessageListenerBeanPostProcessor.AnnotationEnhancer;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.lang.reflect.AnnotatedElement;
import java.util.Map;

/**
 * 将 YAML 中的消费端数值配置写入官方 {@code @RocketMQMessageListener} 注解。
 *
 * <p>RocketMQ v5 Starter 2.3.6 只会对注解中的 String 属性调用 Spring 的占位符解析；
 * {@code sslEnabled/requestTimeout/线程数/缓存大小} 等 boolean、int 属性会直接读取注解值。
 * 官方 Starter 在 {@code RocketMQMessageListenerBeanPostProcessor} 中提供了这个扩展点，
 * 因此这里在容器注册前<strong>原地修改</strong>注解属性 Map，使两类属性最终都来自同一套
 * {@link RocketMqLearningProperties}。</p>
 *
 * <p>为什么必须原地修改：Starter 2.3.6 的组合逻辑最终返回的是最初的 attributes Map，
 * 不会使用 enhancer 新建后返回的 Map；所以 {@code put} 是有意为之，不能改成 copy 后 return。</p>
 */
@Component
@RequiredArgsConstructor
public class RocketMqListenerAnnotationEnhancer implements AnnotationEnhancer, Ordered {

    private final RocketMqLearningProperties properties;

    @Override
    public Map<String, Object> apply(Map<String, Object> attributes, AnnotatedElement element) {
        RocketMqLearningProperties.Consumer consumer = properties.getConsumer();

        // 以下字段正是官方注解无法用 ${...} 转成 boolean/int 的部分。
        attributes.put("sslEnabled", consumer.getSslEnabled());
        attributes.put("requestTimeout", consumer.getRequestTimeout());
        attributes.put("maxCachedMessageCount", consumer.getMaxCachedMessageCount());
        attributes.put("maxCacheMessageSizeInBytes", consumer.getMaxCacheMessageSizeInBytes());
        attributes.put("consumptionThreadCount", consumer.getConsumptionThreadCount());
        // filterExpressionType 虽然是 String，但 Starter 2.3.6 不会对它调用 resolvePlaceholders，
        // 也必须由增强器写入最终值；否则字面量 ${...} 会被当成非 TAG 类型并错误回落到 SQL92。
        attributes.put("filterExpressionType", consumer.getFilterExpressionType());

        /*
         * 不在这里写 accessKey/secretKey/namespace：Starter 注册时先对原始
         * 注解的 accessKey、secretKey、topic、endpoints 调用 Assert.hasText，再解析占位符。
         * 无鉴权环境最终值可以为空，但原始注解必须保持 ${...} 这个非空占位符文本；String 属性直接
         * 写在各 Listener 注解中，交给 Starter 后续 resolvePlaceholders。
         */
        return attributes;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
