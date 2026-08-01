package com.xt.xiaoxingxing.user.entity;

import com.xt.xiaoxingxing.user.enums.UserStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {

    private Long id;
    private String username;
    private String email;
    private UserStatus status;
    private LocalDateTime createTime;
}
