package com.xt.xiaoxingxing.playground.features.mongo.service;

import com.xt.xiaoxingxing.playground.features.mongo.dto.request.SubmissionCreateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.response.SubmissionResponse;
import com.xt.xiaoxingxing.shared.core.response.PageResult;

public interface MongoSubmissionService {

    SubmissionResponse submit(String questionnaireId, SubmissionCreateRequest request);

    SubmissionResponse getById(String id);

    PageResult<SubmissionResponse> pageByQuestionnaire(String questionnaireId, int pageNum, int pageSize);

    PageResult<SubmissionResponse> pageByUser(Long userId, int pageNum, int pageSize);
}
