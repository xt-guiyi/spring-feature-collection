package com.xt.xiaoxingxing.playground.rabbitmq.config;

import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.OffsetSpecification;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.rabbit.stream.config.StreamRabbitListenerContainerFactory;
import org.springframework.rabbit.stream.listener.StreamListenerContainer;
import org.springframework.rabbit.stream.producer.RabbitStreamTemplate;

/**
 * RabbitMQ 原生 Stream 协议配置。
 *
 * <p>普通 RabbitTemplate 通过 AMQP 0-9-1 的 5672 端口；RabbitStreamTemplate 使用 Stream 协议的
 * 5552 端口。两者都连接同一个 RabbitMQ，但客户端协议和消费模型不同。</p>
 */
@Configuration
public class RabbitStreamConfig {

    @Bean(destroyMethod = "close")
    public Environment rabbitStreamEnvironment(RabbitMqLearningProperties properties) {
        RabbitMqLearningProperties.Stream stream = properties.getStream();
        return Environment.builder()
                .host(stream.getHost())
                .port(stream.getPort())
                .username(stream.getUsername())
                .password(stream.getPassword())
                .build();
    }

    @Bean
    public RabbitStreamTemplate rabbitStreamTemplate(Environment rabbitStreamEnvironment) {
        return new RabbitStreamTemplate(rabbitStreamEnvironment, RabbitMqNames.ORDER_AUDIT_STREAM);
    }

    @Bean(name = "rabbitStreamListenerContainerFactory")
    public RabbitListenerContainerFactory<StreamListenerContainer> rabbitStreamListenerContainerFactory(
            Environment rabbitStreamEnvironment) {
        StreamRabbitListenerContainerFactory factory =
                new StreamRabbitListenerContainerFactory(rabbitStreamEnvironment);
        factory.setNativeListener(true);
        factory.setConsumerCustomizer((id, builder) -> builder
                // 有名字的消费者可以让 Broker 保存 offset；匿名消费者无法在重启后定位上次进度。
                .name("pg-order-audit-reader-v1")
                // 第一次启动从 Stream 当前仍保留的第一条消息开始，而不是只等待新消息。
                .offset(OffsetSpecification.first())
                // 由业务处理成功后显式 context.storeOffset()，避免先推进 offset 再处理失败。
                .manualTrackingStrategy());
        return factory;
    }
}
