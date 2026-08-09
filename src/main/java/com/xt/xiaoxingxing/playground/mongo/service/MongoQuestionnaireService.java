package com.xt.xiaoxingxing.playground.mongo.service;

import com.xt.xiaoxingxing.playground.mongo.dto.request.QuestionCreateRequest;
import com.xt.xiaoxingxing.playground.mongo.dto.request.QuestionUpdateRequest;
import com.xt.xiaoxingxing.playground.mongo.dto.request.QuestionnaireCreateRequest;
import com.xt.xiaoxingxing.playground.mongo.dto.request.QuestionnaireQueryRequest;
import com.xt.xiaoxingxing.playground.mongo.dto.request.QuestionnaireUpdateRequest;
import com.xt.xiaoxingxing.playground.mongo.vo.QuestionnaireVO;
import com.xt.xiaoxingxing.shared.common.PageResult;

public interface MongoQuestionnaireService {

    QuestionnaireVO create(QuestionnaireCreateRequest request);

    QuestionnaireVO getById(String id);

    PageResult<QuestionnaireVO> page(QuestionnaireQueryRequest request);

    QuestionnaireVO update(String id, long expectedVersion, QuestionnaireUpdateRequest request);

    QuestionnaireVO addQuestion(String id, long expectedVersion, QuestionCreateRequest request);

    QuestionnaireVO updateQuestion(String id, String questionId, long expectedVersion,
                                   QuestionUpdateRequest request);

    QuestionnaireVO deleteQuestion(String id, String questionId, long expectedVersion);

    QuestionnaireVO publish(String id, long expectedVersion);

    QuestionnaireVO close(String id, long expectedVersion);

    boolean deleteDraft(String id, long expectedVersion);
}
