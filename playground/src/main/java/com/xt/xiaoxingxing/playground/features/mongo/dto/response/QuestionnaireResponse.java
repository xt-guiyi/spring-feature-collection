package com.xt.xiaoxingxing.playground.features.mongo.dto.response;

import com.xt.xiaoxingxing.playground.features.mongo.enums.QuestionnaireStatus;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** 问卷接口返回对象，MongoDB 文档和 playground 本地用户摘要在这里汇合。 */
@Data
public class QuestionnaireResponse {

    private String id;

    private String title;

    private String description;

    private QuestionnaireStatus status;

    private UserSummaryResponse createdBy;

    private List<QuestionResponse> questions;

    private Long version;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant publishedAt;

    private Instant closedAt;
}
