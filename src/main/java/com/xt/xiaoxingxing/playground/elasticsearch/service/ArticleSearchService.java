package com.xt.xiaoxingxing.playground.elasticsearch.service;

import com.xt.xiaoxingxing.playground.elasticsearch.dto.ArticleAggregationRequest;
import com.xt.xiaoxingxing.playground.elasticsearch.dto.ArticleCursorRequest;
import com.xt.xiaoxingxing.playground.elasticsearch.dto.ArticleHybridRequest;
import com.xt.xiaoxingxing.playground.elasticsearch.dto.ArticleKnnRequest;
import com.xt.xiaoxingxing.playground.elasticsearch.dto.ArticleSearchRequest;
import com.xt.xiaoxingxing.playground.elasticsearch.vo.ArticleAggregationVO;
import com.xt.xiaoxingxing.playground.elasticsearch.vo.ArticleCursorPageVO;
import com.xt.xiaoxingxing.playground.elasticsearch.vo.ArticleSearchHitVO;
import com.xt.xiaoxingxing.shared.common.PageResult;

import java.util.List;

public interface ArticleSearchService {

    /** 按条件分页检索文章。 */
    PageResult<ArticleSearchHitVO> search(ArticleSearchRequest request);

    /** 统计文章的聚合结果。 */
    ArticleAggregationVO aggregations(ArticleAggregationRequest request);

    /** 按标题前缀返回文章补全建议。 */
    List<String> suggestions(String prefix, int size);

    /** 使用游标深分页检索文章。 */
    ArticleCursorPageVO cursor(ArticleCursorRequest request);

    /** 使用向量近邻检索文章。 */
    List<ArticleSearchHitVO> knn(ArticleKnnRequest request);

    /** 结合关键词和向量结果检索文章。 */
    List<ArticleSearchHitVO> hybrid(ArticleHybridRequest request);
}
