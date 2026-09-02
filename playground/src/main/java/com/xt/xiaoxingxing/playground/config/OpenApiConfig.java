package com.xt.xiaoxingxing.playground.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.open-api")
public class OpenApiConfig {

    /** 仅扫描学习模块；这是包结构约束，不属于随环境变化的运行参数。 */
    private static final String PLAYGROUND_BASE_PACKAGE = "com.xt.xiaoxingxing.playground.features";

    @NotBlank
    private String title;

    @NotBlank
    private String version;

    @NotBlank
    private String description;

    @NotBlank
    private String group;

    @NotBlank
    private String displayName;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .version(version)
                        .description(description));
    }

    @Bean
    public GroupedOpenApi playgroundOpenApi() {
        return GroupedOpenApi.builder()
                .group(group)
                .displayName(displayName)
                .packagesToScan(PLAYGROUND_BASE_PACKAGE)
                .build();
    }
}
