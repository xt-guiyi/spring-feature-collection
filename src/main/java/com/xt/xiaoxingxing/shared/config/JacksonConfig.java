package com.xt.xiaoxingxing.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JacksonSerializationProperties.class)
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper(JacksonSerializationProperties properties) {
        /*
         * 项目显式声明 ObjectMapper 后，Spring Boot 默认的 Jackson 自动配置会让位给本 Bean。
         * 如果这里只返回裸 new ObjectMapper()，它不会识别 LocalDateTime；RocketMQ 信封中的
         * occurredAt 在第一次 writeValueAsString 时就会抛 InvalidDefinitionException。
         *
         * findAndRegisterModules 会发现 classpath 中的 Java Time 等官方模块；日期是否输出成时间戳
         * 则严格遵循 spring.jackson.serialization.write-dates-as-timestamps，生产者与消费者可对称解析。
         */
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                properties.getWriteDatesAsTimestamps());
        return mapper;
    }
}
