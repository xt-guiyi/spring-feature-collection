package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PgUserResponse {
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String status;
    private LocalDateTime createdAt;
}
