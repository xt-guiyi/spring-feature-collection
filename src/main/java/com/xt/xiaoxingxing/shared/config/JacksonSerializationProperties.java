package com.xt.xiaoxingxing.shared.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 与项目自定义 ObjectMapper 对应的 Jackson 序列化开关。
 *
 * <p>项目显式创建 ObjectMapper 后，需要自行把 YAML 中的标准 Jackson 配置应用进去；
 * 否则看起来配置存在，实际却不会影响 HTTP 和 RocketMQ 的 JSON 序列化。</p>
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "spring.jackson.serialization")
public class JacksonSerializationProperties {

    @NotNull
    private Boolean writeDatesAsTimestamps;
}
