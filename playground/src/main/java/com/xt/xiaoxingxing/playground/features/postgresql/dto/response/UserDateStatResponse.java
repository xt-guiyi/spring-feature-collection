package com.xt.xiaoxingxing.playground.features.postgresql.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserDateStatResponse {

    private Long userId;
    private String username;
    private LocalDateTime createdAt;
    private LocalDate createDate;
    private Integer createYear;
}
