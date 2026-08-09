package com.xt.xiaoxingxing.playground.mongo.vo;

import lombok.Data;

/** 从 PostgreSQL users 表读取的用户摘要，不复制到 MongoDB 文档中。 */
@Data
public class UserSummaryVO {

    private Long userId;

    private String username;

    private String email;

    private String status;
}
