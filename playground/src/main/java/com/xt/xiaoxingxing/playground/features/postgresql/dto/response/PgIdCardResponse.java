package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import lombok.Data;

@Data
public class PgIdCardResponse {
    private Long id;
    private Long userId;
    private String cardNumber;
    private String realName;
}
