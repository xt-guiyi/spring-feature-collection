package com.xt.xiaoxingxing.user.dto.response;

import com.xt.xiaoxingxing.user.enums.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private UserStatus status;
    private LocalDateTime createTime;
}
