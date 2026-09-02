package com.xt.xiaoxingxing.playground.features.redis.dto.response;

import lombok.Data;

@Data
public class RedisRecordIdResponse {
    private Long timestamp;
    private Long sequence;
    private String value;
}
