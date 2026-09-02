package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import jakarta.annotation.Nullable;
import lombok.Data;

@Data
public class UserWithIdCardResponse {

    private Long userId;
    private String username;
    private String email;
    @Nullable
    private String cardNumber;
    private String realName;
}
