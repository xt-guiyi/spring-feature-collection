package com.xt.xiaoxingxing.playground.redis.enums;

import lombok.Getter;

@Getter
public enum RedisDemoStatus {

    ACTIVE("有效"),
    EXPIRED("已过期");

    private final String desc;

    RedisDemoStatus(String desc) {
        this.desc = desc;
    }
}
