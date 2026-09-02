package com.xt.xiaoxingxing.playground.features.elasticsearch.service;

import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleAggregationRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleCursorRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleHybridRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleKnnRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleSearchRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleAggregationResponse;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleCursorPageResponse;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleSearchHitResponse;
import com.xt.xiaoxingxing.shared.core.response.PageResult;

import java.util.List;

public interface ArticleSearchService {

    /** 按条件分页检索文章。 */
    PageResult<ArticleSearchHitResponse> search(ArticleSearchRequest request);

    /** 统计文章的聚合结果。 */
    ArticleAggregationResponse aggregations(ArticleAggregationRequest request);

    /** 按标题前缀返回文章补全建议。 */
    List<String> suggestions(String prefix, int size);

    /** 使用 completion suggester 按标题前缀返回文章补全建议。 */
    List<String> completionSuggestions(String prefix, int size);

    /** 使用游标深分页检索文章。 */
    ArticleCursorPageResponse cursor(ArticleCursorRequest request);

    /** 使用向量近邻检索文章。 */
    List<ArticleSearchHitResponse> knn(ArticleKnnRequest request);

    /** 结合关键词和向量结果检索文章。 */
    List<ArticleSearchHitResponse> hybrid(ArticleHybridRequest request);
}
