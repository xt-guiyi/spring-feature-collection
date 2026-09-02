package com.xt.xiaoxingxing.playground.features.postgresql.dto.request;

import lombok.Data;

@Data
public class PgIdCardRequest {
    private Long id;
    private Long userId;
    private String cardNumber;
    private String realName;
}
