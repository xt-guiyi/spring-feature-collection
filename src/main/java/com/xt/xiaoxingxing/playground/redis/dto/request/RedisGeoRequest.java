package com.xt.xiaoxingxing.playground.redis.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RedisGeoRequest {

    @NotBlank(message = "key 不能为空")
    private String key;

    @NotBlank(message = "member 不能为空")
    private String member;

    @NotNull(message = "经度不能为空")
    private Double longitude;

    @NotNull(message = "纬度不能为空")
    private Double latitude;
}
