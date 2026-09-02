package com.xt.xiaoxingxing.playground.features.postgresql.vo;

import jakarta.annotation.Nullable;
import lombok.Data;

@Data
public class UserWithIdCardVO {

    private Long userId;
    private String username;
    private String email;
    @Nullable
    private String cardNumber;
    private String realName;
}
