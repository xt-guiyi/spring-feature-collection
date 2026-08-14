package com.xt.xiaoxingxing.shared.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * HTTP 跨域配置。
 *
 * <p>允许哪些前端来源是部署策略，不应随着 Java 代码一起固化；尤其生产环境不能沿用
 * localhost 开发地址。因此这里不设置任何默认值，并由 application*.yaml 完整提供。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.cors")
public class CorsProperties {

    @NotNull
    private Boolean enabled;

    @NotBlank
    private String pathPattern;

    @NotEmpty
    private List<@NotBlank String> allowedOrigins;

    @NotEmpty
    private List<@NotBlank String> allowedMethods;

    @NotEmpty
    private List<@NotBlank String> allowedHeaders;

    @NotNull
    private Boolean allowCredentials;
}
