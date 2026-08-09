package com.xt.xiaoxingxing.playground.rabbitmq.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** TTL 重试案例请求；failTimes 大于系统最大重试次数时最终进入 DLQ。 */
@Data
public class RabbitRetryDemoRequest {

    @NotBlank(message = "message不能为空")
    private String message;

    @Min(value = 0, message = "failTimes不能小于0")
    @Max(value = 10, message = "failTimes不能大于10")
    @NotNull(message = "failTimes不能为空")
    private Integer failTimes = 2;
}
