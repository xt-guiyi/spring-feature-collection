package com.xt.xiaoxingxing.playground.features.elasticsearch.repository;

import com.xt.xiaoxingxing.playground.features.elasticsearch.document.ArticleDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ArticleRepository extends ElasticsearchRepository<ArticleDocument, String> {
}
