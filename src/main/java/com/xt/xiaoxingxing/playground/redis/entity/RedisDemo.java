package com.xt.xiaoxingxing.playground.redis.entity;

import com.xt.xiaoxingxing.playground.redis.enums.RedisDemoStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisDemo {

    private Long id;
    private String name;
    private RedisDemoStatus status;
    private LocalDateTime createTime;
}
