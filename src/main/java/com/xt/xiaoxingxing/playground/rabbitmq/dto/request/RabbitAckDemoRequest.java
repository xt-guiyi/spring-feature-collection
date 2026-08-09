package com.xt.xiaoxingxing.playground.rabbitmq.dto.request;

import com.xt.xiaoxingxing.playground.rabbitmq.enums.RabbitAckAction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 手动 ACK、NACK、Reject 和重试行为请求。 */
@Data
public class RabbitAckDemoRequest {

    @NotBlank(message = "message不能为空")
    private String message;

    @NotNull(message = "action不能为空")
    private RabbitAckAction action;

    /** RETRY_THEN_SUCCESS 时，前多少次处理主动抛出异常。 */
    @Min(value = 0, message = "failTimes不能小于0")
    @Max(value = 10, message = "failTimes不能大于10")
    @NotNull(message = "failTimes不能为空")
    private Integer failTimes = 2;
}
