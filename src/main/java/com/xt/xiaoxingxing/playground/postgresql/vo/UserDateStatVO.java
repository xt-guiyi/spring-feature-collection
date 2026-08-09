package com.xt.xiaoxingxing.playground.postgresql.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserDateStatVO {

    private Long userId;
    private String username;
    private LocalDateTime createdAt;
    private LocalDate createDate;
    private Integer createYear;
}
