package com.xt.xiaoxingxing.playground.rocketmq.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Broker 重试/DLQ 演示请求。
 *
 * <p>允许 failTimes 大于当前运行配置的最大重试次数，才能观察消息在 Broker 重投耗尽后进入该消费组 DLQ 的行为。</p>
 */
@Data
public class RocketRetryMessageRequest {

    @NotBlank(message = "text不能为空")
    @Size(max = 500, message = "text最多500个字符")
    private String text;

    @NotNull(message = "failTimes不能为空")
    @Min(value = 0, message = "failTimes不能小于0")
    @Max(value = 100, message = "failTimes不能大于100")
    private Integer failTimes = 2;
}
