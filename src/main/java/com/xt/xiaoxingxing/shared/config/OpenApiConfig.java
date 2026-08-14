package com.xt.xiaoxingxing.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiConfig {

    /** 仅扫描学习模块；这是包结构约束，不属于随环境变化的运行参数。 */
    private static final String PLAYGROUND_BASE_PACKAGE = "com.xt.xiaoxingxing.playground";

    @Bean
    public OpenAPI openAPI(OpenApiProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title(properties.getTitle())
                        .version(properties.getVersion())
                        .description(properties.getDescription()));
    }

    @Bean
    public GroupedOpenApi playgroundOpenApi(OpenApiProperties properties) {
        return GroupedOpenApi.builder()
                .group(properties.getGroup())
                .displayName(properties.getDisplayName())
                .packagesToScan(PLAYGROUND_BASE_PACKAGE)
                .build();
    }
}
