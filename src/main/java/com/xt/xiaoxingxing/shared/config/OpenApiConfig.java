package com.xt.xiaoxingxing.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API 文档")
                        .version("1.0.0")
                        .description("项目接口文档"));
    }

    @Bean
    public GroupedOpenApi playgroundOpenApi() {
        return GroupedOpenApi.builder()
                .group("playground")
                .displayName("playground")
                .packagesToScan("com.xt.xiaoxingxing.playground")
                .build();
    }
}
