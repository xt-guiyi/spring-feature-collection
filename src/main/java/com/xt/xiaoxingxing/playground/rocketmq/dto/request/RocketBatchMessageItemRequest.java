package com.xt.xiaoxingxing.playground.rocketmq.dto.request;

import com.xt.xiaoxingxing.playground.rocketmq.config.RocketMqNames;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 应用层批量请求中的一项；每项会生成独立信封和独立发布结果。 */
@Data
public class RocketBatchMessageItemRequest {

    @NotBlank(message = "items中的text不能为空")
    @Size(max = 500, message = "items中的text最多500个字符")
    private String text;

    @NotBlank(message = "items中的tag不能为空")
    @Pattern(regexp = "[A-Za-z0-9_]+", message = "items中的tag只能包含字母、数字或下划线")
    private String tag = RocketMqNames.TAG_DEMO;
}
