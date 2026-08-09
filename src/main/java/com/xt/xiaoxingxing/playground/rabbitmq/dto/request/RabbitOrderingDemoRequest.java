package com.xt.xiaoxingxing.playground.rabbitmq.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 一次发送同一业务键的连续序号，用于观察 FIFO 与单活跃消费者。 */
@Data
public class RabbitOrderingDemoRequest {

    @NotBlank(message = "businessKey不能为空")
    private String businessKey;

    @Min(value = 1, message = "count至少为1")
    @Max(value = 100, message = "count最多为100")
    @NotNull(message = "count不能为空")
    private Integer count = 10;
}
