package com.xt.xiaoxingxing.playground.rabbitmq.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Direct 和 Topic 接口的路由消息请求。 */
@Data
public class RabbitRoutingMessageRequest {

    @NotBlank(message = "routingKey不能为空")
    private String routingKey;

    @NotBlank(message = "message不能为空")
    private String message;
}
