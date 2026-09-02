package com.xt.xiaoxingxing.playground.features.redis.entity;

import com.xt.xiaoxingxing.playground.features.redis.enums.RedisDemoStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisDemo {

    private Long id;
    private String name;
    private RedisDemoStatus status;
    private LocalDateTime createTime;
}
