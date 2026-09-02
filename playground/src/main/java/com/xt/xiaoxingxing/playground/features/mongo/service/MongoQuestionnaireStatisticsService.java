package com.xt.xiaoxingxing.playground.features.mongo.service;

import com.xt.xiaoxingxing.playground.features.mongo.dto.response.QuestionnaireStatisticsResponse;

public interface MongoQuestionnaireStatisticsService {

    QuestionnaireStatisticsResponse statistics(String questionnaireId);
}
