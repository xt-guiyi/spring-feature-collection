package com.xt.xiaoxingxing.playground.features.mongo.dto.response;

import lombok.Data;

import java.util.List;

/** 问卷聚合统计结果。 */
@Data
public class QuestionnaireStatisticsResponse {

    private String questionnaireId;

    private String questionnaireTitle;

    private Long totalSubmissions;

    private List<QuestionStatisticResponse> questions;
}
