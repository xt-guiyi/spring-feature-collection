package com.xt.xiaoxingxing.playground.features.redis.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RedisBitmapRequest {

    @NotBlank(message = "key 不能为空")
    private String key;

    @NotNull(message = "offset 不能为空")
    private Long offset;

    @NotNull(message = "value 不能为空")
    private Boolean value;
}
