package com.xt.xiaoxingxing.playground.mongo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tools.jackson.databind.JsonNode;

/** value 使用 JsonNode 接收前端动态 JSON，进入 Service 后再按题型转换为明确 BSON 值。 */
@Data
public class AnswerRequest {

    @NotBlank(message = "题目ID不能为空")
    private String questionId;

    @NotNull(message = "答案值不能为空")
    private JsonNode value;
}
