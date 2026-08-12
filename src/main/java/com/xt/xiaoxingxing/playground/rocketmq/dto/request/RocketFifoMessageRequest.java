package com.xt.xiaoxingxing.playground.rocketmq.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 同一业务键连续发送序号，用于观察 FIFO Topic 的 MessageGroup 内顺序。 */
@Data
public class RocketFifoMessageRequest {

    @NotBlank(message = "businessKey不能为空")
    private String businessKey;

    @NotNull(message = "count不能为空")
    @Min(value = 1, message = "count至少为1")
    @Max(value = 100, message = "count最多为100")
    private Integer count = 10;
}
