package com.xt.xiaoxingxing.playground.features.redis.dto.response;

import lombok.Data;

@Data
public class RedisDistanceResponse {
    private double value;
    private String metric;
    private double normalizedValue;
    private String unit;
}
