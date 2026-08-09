package com.xt.xiaoxingxing.playground.mongo.service;

import com.xt.xiaoxingxing.playground.mongo.dto.request.SubmissionCreateRequest;
import com.xt.xiaoxingxing.playground.mongo.vo.SubmissionVO;
import com.xt.xiaoxingxing.shared.common.PageResult;

public interface MongoSubmissionService {

    SubmissionVO submit(String questionnaireId, SubmissionCreateRequest request);

    SubmissionVO getById(String id);

    PageResult<SubmissionVO> pageByQuestionnaire(String questionnaireId, int pageNum, int pageSize);

    PageResult<SubmissionVO> pageByUser(Long userId, int pageNum, int pageSize);
}
