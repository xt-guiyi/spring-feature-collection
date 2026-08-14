package com.xt.xiaoxingxing.playground.rocketmq.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 普通、异步和 Tag 过滤演示的文本消息请求。 */
@Data
public class RocketTextMessageRequest {

    @NotBlank(message = "text不能为空")
    @Size(max = 500, message = "text最多500个字符")
    private String text;

    /**
     * Tag 只允许保守字符集，避免破坏消息路由语义。
     * 请求未传或显式传空字符串时，由 Service 使用 YAML 中的演示 Tag；不再在 DTO 写 Java 默认值。
     */
    @Pattern(regexp = "^$|[A-Za-z0-9_]+$", message = "tag只能为空，或包含字母、数字、下划线")
    private String tag;
}
