package com.xt.xiaoxingxing.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        /*
         * 项目显式声明 ObjectMapper 后，Spring Boot 默认的 Jackson 自动配置会让位给本 Bean。
         * 如果这里只返回裸 new ObjectMapper()，它不会识别 LocalDateTime；RocketMQ 信封中的
         * occurredAt 在第一次 writeValueAsString 时就会抛 InvalidDefinitionException。
         *
         * findAndRegisterModules 会发现 classpath 中的 Java Time 等官方模块；关闭时间戳数组后，
         * LocalDateTime 使用 "2026-08-12T10:00:00" 这类可读 ISO 字符串，生产者与消费者也能对称解析。
         */
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
