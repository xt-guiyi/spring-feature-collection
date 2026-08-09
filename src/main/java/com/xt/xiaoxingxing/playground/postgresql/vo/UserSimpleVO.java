package com.xt.xiaoxingxing.playground.postgresql.vo;

import lombok.Data;

@Data
public class UserSimpleVO {

    private Long userId;
    private String username;
    private String email;
    private String status;
}
