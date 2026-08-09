package com.xt.xiaoxingxing.playground.rabbitmq.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 原生 Stream 自定义事件发布请求。 */
@Data
public class RabbitStreamEventRequest {

    @NotBlank(message = "eventType不能为空")
    private String eventType;

    @NotBlank(message = "aggregateId不能为空")
    private String aggregateId;

    @NotNull(message = "payload不能为空")
    private JsonNode payload;
}
