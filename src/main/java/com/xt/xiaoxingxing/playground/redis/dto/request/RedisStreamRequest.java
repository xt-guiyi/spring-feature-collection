package com.xt.xiaoxingxing.playground.redis.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
public class RedisStreamRequest {

    @NotBlank(message = "key 不能为空")
    private String key;

    @NotEmpty(message = "fields 不能为空")
    private Map<String, String> fields;
}
