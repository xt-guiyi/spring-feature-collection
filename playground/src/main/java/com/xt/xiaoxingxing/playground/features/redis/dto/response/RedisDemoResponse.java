package com.xt.xiaoxingxing.playground.features.redis.dto.response;

import com.xt.xiaoxingxing.playground.features.redis.enums.RedisDemoStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RedisDemoResponse {
    private Long id;
    private String name;
    private RedisDemoStatus status;
    private LocalDateTime createTime;
}
