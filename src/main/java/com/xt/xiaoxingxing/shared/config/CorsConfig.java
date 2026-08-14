package com.xt.xiaoxingxing.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    /**
     * 仅当 application.cors.enabled=true 时注册跨域规则。
     *
     * <p>不再用 @Profile("dev") 把策略绑定到环境名：有些测试环境需要跨域，某些开发环境
     * 反而不需要。开关、来源、方法和凭证策略全部从 YAML 读取，部署时无需修改代码。</p>
     */
    @Bean
    @ConditionalOnProperty(prefix = "application.cors", name = "enabled", havingValue = "true")
    public WebMvcConfigurer corsConfigurer(CorsProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping(properties.getPathPattern())
                        .allowedOrigins(properties.getAllowedOrigins().toArray(String[]::new))
                        .allowedMethods(properties.getAllowedMethods().toArray(String[]::new))
                        .allowedHeaders(properties.getAllowedHeaders().toArray(String[]::new))
                        .allowCredentials(properties.getAllowCredentials());
            }
        };
    }
}
