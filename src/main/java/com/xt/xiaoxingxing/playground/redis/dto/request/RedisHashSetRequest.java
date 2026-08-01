package com.xt.xiaoxingxing.playground.redis.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RedisHashSetRequest {

    @NotBlank(message = "hash key 不能为空")
    private String hashKey;

    @NotBlank(message = "field 不能为空")
    private String field;

    @NotBlank(message = "value 不能为空")
    private String value;
}
