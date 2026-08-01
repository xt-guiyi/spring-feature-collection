package com.xt.xiaoxingxing.playground.redis.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RedisSetRequest {

    @NotBlank(message = "key 不能为空")
    private String key;

    @NotBlank(message = "value 不能为空")
    private String value;

    /**
     * 过期时间，单位秒。为空表示不过期。
     */
    private Long expireSeconds;
}
