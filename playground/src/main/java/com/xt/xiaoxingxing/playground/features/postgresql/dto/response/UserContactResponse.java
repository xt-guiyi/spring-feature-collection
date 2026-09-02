package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import lombok.Data;

@Data
public class UserContactResponse {

    private Long userId;
    private String username;
    private String email;
    private String phone;
}
