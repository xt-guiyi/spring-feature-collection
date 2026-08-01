package com.xt.xiaoxingxing.playground.redis.dto.response;

import lombok.Data;

@Data
public class RedisValueResponse {

    private String key;
    private String value;
    private Long ttl;
}
