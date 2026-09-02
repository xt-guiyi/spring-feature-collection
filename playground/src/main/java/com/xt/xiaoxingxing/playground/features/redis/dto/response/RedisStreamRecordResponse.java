package com.xt.xiaoxingxing.playground.features.redis.dto.response;

import lombok.Data;
import java.util.Map;

@Data
public class RedisStreamRecordResponse {
    private String stream;
    private RedisRecordIdResponse id;
    private Map<Object, Object> value;
}
