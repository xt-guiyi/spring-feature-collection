package com.xt.xiaoxingxing.playground.mongo.repository;

import com.xt.xiaoxingxing.playground.mongo.document.QuestionnaireSubmissionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/** 答卷的简单插入、查询、计数和重复提交预检查。 */
public interface QuestionnaireSubmissionRepository
        extends MongoRepository<QuestionnaireSubmissionDocument, String> {

    boolean existsByQuestionnaireIdAndUserId(String questionnaireId, Long userId);

    long countByQuestionnaireId(String questionnaireId);
}
