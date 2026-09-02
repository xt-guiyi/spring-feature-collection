package com.xt.xiaoxingxing.playground.features.mongo.dto.response;

import lombok.Data;

/** 从 playground 自己的 PostgreSQL users 表读取的用户摘要。 */
@Data
public class UserSummaryResponse {

    private Long userId;

    private String username;

    private String email;

    private String status;
}
