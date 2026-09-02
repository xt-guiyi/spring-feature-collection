package com.xt.xiaoxingxing.playground.features.redis.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RedisZSetRequest {

    @NotBlank(message = "key 不能为空")
    private String key;

    @NotBlank(message = "value 不能为空")
    private String value;

    @NotNull(message = "score 不能为空")
    private Double score;
}
