package com.xt.xiaoxingxing.playground.mongo.service;

import com.xt.xiaoxingxing.playground.mongo.vo.QuestionnaireStatisticsVO;

public interface MongoQuestionnaireStatisticsService {

    QuestionnaireStatisticsVO statistics(String questionnaireId);
}
