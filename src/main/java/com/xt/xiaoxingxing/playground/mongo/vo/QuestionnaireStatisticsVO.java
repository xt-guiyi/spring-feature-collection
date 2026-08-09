package com.xt.xiaoxingxing.playground.mongo.vo;

import lombok.Data;

import java.util.List;

/** 问卷聚合统计结果。 */
@Data
public class QuestionnaireStatisticsVO {

    private String questionnaireId;

    private String questionnaireTitle;

    private Long totalSubmissions;

    private List<QuestionStatisticVO> questions;
}
