package com.xt.xiaoxingxing.playground.elasticsearch.repository;

import com.xt.xiaoxingxing.playground.elasticsearch.document.ArticleDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ArticleRepository extends ElasticsearchRepository<ArticleDocument, String> {
}
