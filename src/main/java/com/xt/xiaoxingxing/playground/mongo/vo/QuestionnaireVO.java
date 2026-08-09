package com.xt.xiaoxingxing.playground.mongo.vo;

import com.xt.xiaoxingxing.playground.mongo.enums.QuestionnaireStatus;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** 问卷接口返回对象，MongoDB文档和PostgreSQL用户信息在这里汇合。 */
@Data
public class QuestionnaireVO {

    private String id;

    private String title;

    private String description;

    private QuestionnaireStatus status;

    private UserSummaryVO createdBy;

    private List<QuestionVO> questions;

    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant publishedAt;

    private Instant closedAt;
}
