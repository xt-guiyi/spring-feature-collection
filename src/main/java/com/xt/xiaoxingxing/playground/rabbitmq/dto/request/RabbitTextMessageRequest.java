package com.xt.xiaoxingxing.playground.rabbitmq.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Fanout 和 Mandatory Return 不需要调用者提供有效 Routing Key。 */
@Data
public class RabbitTextMessageRequest {

    @NotBlank(message = "message不能为空")
    private String message;
}
