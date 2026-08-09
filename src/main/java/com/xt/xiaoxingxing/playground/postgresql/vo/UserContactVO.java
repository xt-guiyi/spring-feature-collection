package com.xt.xiaoxingxing.playground.postgresql.vo;

import lombok.Data;

@Data
public class UserContactVO {

    private Long userId;
    private String username;
    private String email;
    private String phone;
}
