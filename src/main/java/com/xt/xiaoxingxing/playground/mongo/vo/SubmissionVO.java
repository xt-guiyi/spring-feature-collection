package com.xt.xiaoxingxing.playground.mongo.vo;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/** 答卷接口返回对象。 */
@Data
public class SubmissionVO {

    private String id;

    private String questionnaireId;

    private String questionnaireTitle;

    private UserSummaryVO user;

    private List<AnswerVO> answers;

    private Instant submittedAt;
}
