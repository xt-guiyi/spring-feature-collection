package com.xt.xiaoxingxing.playground.mongo.repository;

import com.xt.xiaoxingxing.playground.mongo.document.QuestionnaireDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

/** 简单单文档 CRUD 使用 Repository；动态查询和数组更新留给 MongoTemplate。 */
public interface QuestionnaireRepository extends MongoRepository<QuestionnaireDocument, String> {
}
