package com.xt.xiaoxingxing.playground.features.mongo.dto.response;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/** 答卷接口返回对象。 */
@Data
public class SubmissionResponse {

    private String id;

    private String questionnaireId;

    private String questionnaireTitle;

    private UserSummaryResponse user;

    private List<AnswerResponse> answers;

    private Instant submittedAt;
}
