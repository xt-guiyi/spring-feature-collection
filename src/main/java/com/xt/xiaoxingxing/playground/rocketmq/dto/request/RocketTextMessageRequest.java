package com.xt.xiaoxingxing.playground.rocketmq.dto.request;

import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
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

    /** Tag 只允许保守字符集，避免把 destination 的 {@code topic:tag} 格式拼坏。 */
    @NotBlank(message = "tag不能为空")
    @Pattern(regexp = "[A-Za-z0-9_]+", message = "tag只能包含字母、数字或下划线")
    private String tag = RocketMqNames.TAG_DEMO;
}
