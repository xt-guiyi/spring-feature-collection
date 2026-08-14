package com.xt.xiaoxingxing.shared.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OpenAPI 展示元数据。
 *
 * <p>扫描 Playground 包是代码结构的一部分，继续放在 Java 常量中；标题、版本、说明和
 * 页面分组名称是可随部署或发布版本调整的展示配置，因此由 YAML 提供。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "application.open-api")
public class OpenApiProperties {

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
}
