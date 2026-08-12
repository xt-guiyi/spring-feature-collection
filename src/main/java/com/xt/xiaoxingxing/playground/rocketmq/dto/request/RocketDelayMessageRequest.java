package com.xt.xiaoxingxing.playground.rocketmq.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 自定义投递延迟演示；时间单位明确为秒，避免 HTTP 调用者误把毫秒当秒。 */
@Data
public class RocketDelayMessageRequest {

    @NotBlank(message = "text不能为空")
    @Size(max = 500, message = "text最多500个字符")
    private String text;

    @NotNull(message = "delaySeconds不能为空")
    @Min(value = 1, message = "delaySeconds至少为1秒")
    @Max(value = 86_400, message = "delaySeconds最多为86400秒")
    private Long delaySeconds;
}
