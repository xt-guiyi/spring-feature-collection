package com.xt.xiaoxingxing.playground.features.mongo.service;

import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionCreateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionUpdateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionnaireCreateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionnaireQueryRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionnaireUpdateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.response.QuestionnaireResponse;
import com.xt.xiaoxingxing.shared.core.response.PageResult;

public interface MongoQuestionnaireService {

    QuestionnaireResponse create(QuestionnaireCreateRequest request);

    QuestionnaireResponse getById(String id);

    PageResult<QuestionnaireResponse> page(QuestionnaireQueryRequest request);

    QuestionnaireResponse update(String id, long expectedVersion, QuestionnaireUpdateRequest request);

    QuestionnaireResponse addQuestion(String id, long expectedVersion, QuestionCreateRequest request);

    QuestionnaireResponse updateQuestion(String id, String questionId, long expectedVersion,
                                   QuestionUpdateRequest request);

    QuestionnaireResponse deleteQuestion(String id, String questionId, long expectedVersion);

    QuestionnaireResponse publish(String id, long expectedVersion);

    QuestionnaireResponse close(String id, long expectedVersion);

    boolean deleteDraft(String id, long expectedVersion);
}
