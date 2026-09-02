package com.xt.xiaoxingxing.playground.features.redis.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RedisHyperLogLogRequest {

    @NotBlank(message = "key 不能为空")
    private String key;

    @NotBlank(message = "value 不能为空")
    private String value;
}
