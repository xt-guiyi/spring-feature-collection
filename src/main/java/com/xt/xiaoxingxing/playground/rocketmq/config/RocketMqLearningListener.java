package com.xt.xiaoxingxing.playground.rocketmq.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 RocketMQ 学习模块的监听器。
 *
 * <p>当 {@code playground.rocketmq.enabled=false} 时，Spring 根本不会创建标记该注解的
 * Listener Bean，官方 Starter 的后处理器也就不会注册 gRPC 消费容器，更不会连接 Broker。
 * 生产端 Service/Controller 仍保留，方便学习者明确区分“停止后台消费”与“隐藏 HTTP 接口”。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(prefix = "playground.rocketmq", name = "enabled", havingValue = "true")
public @interface RocketMqLearningListener {
}
